package com.guest_platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.guest_platform.config.PublicRateLimitProperties;
import com.guest_platform.config.PublicRateLimitProperties.Limit;

class IdentityRateLimitServiceTest {
    @Test
    void accountIdentitiesAreIsolatedAndRepeatedAttemptsAreLimited() {
        PublicRateLimitProperties properties = new PublicRateLimitProperties();
        properties.setLogin(new Limit(2, 900));
        PublicRateLimiter limiter = new PublicRateLimiter(properties,
                Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));
        IdentityRateLimitService service = new IdentityRateLimitService(limiter);

        service.check(PublicRateLimiter.Category.LOGIN, "host-login", " HOST@example.com ");
        service.check(PublicRateLimiter.Category.LOGIN, "host-login", "host@example.com");
        assertThatThrownBy(() -> service.check(PublicRateLimiter.Category.LOGIN, "host-login", "host@example.com"))
                .isInstanceOf(IdentityRateLimitExceededException.class);

        service.check(PublicRateLimiter.Category.LOGIN, "host-login", "other@example.com");
        service.check(PublicRateLimiter.Category.ADMIN_LOGIN, "admin-login", "host@example.com");
    }

    @Test
    void identityKeysAreDomainSeparatedAndNeverContainRawIdentifiers() {
        String email = "owner@example.com";
        String hostKey = PublicRateLimiter.hashIdentity("host-login", email);
        String adminKey = PublicRateLimiter.hashIdentity("admin-login", email);

        assertThat(hostKey).isNotEqualTo(adminKey);
        assertThat(hostKey).doesNotContain(email);
        assertThat(adminKey).doesNotContain(email);
    }
}
