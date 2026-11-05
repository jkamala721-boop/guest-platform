package com.guest_platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.guest_platform.config.PublicRateLimitProperties;
import com.guest_platform.config.PublicRateLimitProperties.Limit;
import com.guest_platform.controller.AdminAuthenticationController;
import com.guest_platform.controller.AuthenticationController;
import com.guest_platform.dto.AdminLoginRequest;
import com.guest_platform.dto.LoginRequest;
import com.guest_platform.dto.RegisterRequest;
import com.guest_platform.exception.AdminInvalidCredentialsException;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.InvalidCredentialsException;
import com.guest_platform.repository.AdminUserRepository;
import com.guest_platform.service.AdminAuditService;
import com.guest_platform.service.AdminAuthenticationService;
import com.guest_platform.service.AdminSessionService;
import com.guest_platform.service.AuthenticationService;
import com.guest_platform.service.HostSessionService;

class IdentityRateLimitControllerTest {
    private static final Instant START = Instant.parse("2026-11-05T00:00:00Z");

    @Test
    void hostLoginAllowsTenAttemptsPerNormalizedEmailThenLimitsBeforeAuthentication() {
        var authentication = mock(AuthenticationService.class);
        when(authentication.login(any())).thenThrow(new InvalidCredentialsException());
        var controller = hostController(authentication);

        for (int attempt = 0; attempt < 10; attempt++) {
            String email = attempt == 9 ? "HOST@example.com" : "host@example.com";
            assertThatThrownBy(() -> controller.login(new LoginRequest(email, "WrongPassword!123")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> controller.login(new LoginRequest("HOST@example.com", "WrongPassword!123")))
                .isInstanceOf(IdentityRateLimitExceededException.class);
        assertThatThrownBy(() -> controller.login(new LoginRequest("other@example.com", "WrongPassword!123")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(authentication, times(11)).login(any());
    }

    @Test
    void adminLoginAllowsFiveAttemptsPerNormalizedEmailThenLimitsBeforeAuthentication() {
        var authentication = mock(AdminAuthenticationService.class);
        when(authentication.login(any())).thenThrow(new AdminInvalidCredentialsException());
        var controller = adminController(authentication);

        for (int attempt = 0; attempt < 5; attempt++) {
            String email = attempt == 4 ? "ADMIN@example.com" : "admin@example.com";
            assertThatThrownBy(() -> controller.login(new AdminLoginRequest(email, "WrongPassword!123")))
                    .isInstanceOf(AdminInvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> controller.login(new AdminLoginRequest("ADMIN@example.com", "WrongPassword!123")))
                .isInstanceOf(IdentityRateLimitExceededException.class);
        assertThatThrownBy(() -> controller.login(new AdminLoginRequest("other-admin@example.com", "WrongPassword!123")))
                .isInstanceOf(AdminInvalidCredentialsException.class);

        verify(authentication, times(6)).login(any());
    }

    @Test
    void registrationAllowsFiveAttemptsPerNormalizedEmailPerHourBeforeRegistration() {
        var authentication = mock(AuthenticationService.class);
        when(authentication.register(any())).thenThrow(new ConflictException("Already registered"));
        var controller = hostController(authentication);

        for (int attempt = 0; attempt < 5; attempt++) {
            String email = attempt == 4 ? "HOST@example.com" : "host@example.com";
            assertThatThrownBy(() -> controller.register(new RegisterRequest(email, "StrongPass!123",
                    "Test Host", "+254711111111", "StrongPass!123")))
                    .isInstanceOf(ConflictException.class);
        }
        assertThatThrownBy(() -> controller.register(new RegisterRequest("HOST@example.com", "StrongPass!123",
                "Test Host", "+254711111111", "StrongPass!123")))
                .isInstanceOf(IdentityRateLimitExceededException.class);
        assertThatThrownBy(() -> controller.register(new RegisterRequest("other@example.com", "StrongPass!123",
                "Test Host", "+254711111111", "StrongPass!123")))
                .isInstanceOf(ConflictException.class);

        verify(authentication, times(6)).register(any());
    }

    @Test
    void configuredIdentityWindowsExpireAtTheirConfiguredBoundaries() {
        MutableClock clock = new MutableClock(START);
        PublicRateLimitProperties properties = new PublicRateLimitProperties();
        properties.setLogin(new Limit(1, 900));
        properties.setAdminLogin(new Limit(1, 900));
        properties.setRegistration(new Limit(1, 3600));
        IdentityRateLimitService service = new IdentityRateLimitService(new PublicRateLimiter(properties, clock));

        service.check(PublicRateLimiter.Category.LOGIN, "host-login", "host@example.com");
        assertThatThrownBy(() -> service.check(PublicRateLimiter.Category.LOGIN, "host-login", "host@example.com"))
                .isInstanceOf(IdentityRateLimitExceededException.class);
        clock.advance(Duration.ofMinutes(15));
        service.check(PublicRateLimiter.Category.LOGIN, "host-login", "host@example.com");

        service.check(PublicRateLimiter.Category.ADMIN_LOGIN, "admin-login", "admin@example.com");
        assertThatThrownBy(() -> service.check(PublicRateLimiter.Category.ADMIN_LOGIN,
                "admin-login", "admin@example.com")).isInstanceOf(IdentityRateLimitExceededException.class);
        clock.advance(Duration.ofMinutes(15));
        service.check(PublicRateLimiter.Category.ADMIN_LOGIN, "admin-login", "admin@example.com");

        service.check(PublicRateLimiter.Category.REGISTRATION, "host-registration", "reg@example.com");
        assertThatThrownBy(() -> service.check(PublicRateLimiter.Category.REGISTRATION,
                "host-registration", "reg@example.com")).isInstanceOf(IdentityRateLimitExceededException.class);
        clock.advance(Duration.ofHours(1));
        service.check(PublicRateLimiter.Category.REGISTRATION, "host-registration", "reg@example.com");
    }

    private AuthenticationController hostController(AuthenticationService authentication) {
        return new AuthenticationController(authentication, mock(HostSessionService.class),
                identityRateLimits(defaultProperties(), Clock.fixed(START, ZoneOffset.UTC)));
    }

    private AdminAuthenticationController adminController(AdminAuthenticationService authentication) {
        return new AdminAuthenticationController(authentication, mock(AdminSessionService.class),
                mock(AdminUserRepository.class), mock(AdminAuditService.class),
                identityRateLimits(defaultProperties(), Clock.fixed(START, ZoneOffset.UTC)));
    }

    private IdentityRateLimitService identityRateLimits(PublicRateLimitProperties properties, Clock clock) {
        return new IdentityRateLimitService(new PublicRateLimiter(properties, clock));
    }

    private PublicRateLimitProperties defaultProperties() {
        return new PublicRateLimitProperties();
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
