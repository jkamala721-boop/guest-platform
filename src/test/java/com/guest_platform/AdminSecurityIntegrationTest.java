package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.entity.AdminRole;
import com.guest_platform.entity.AdminSession;
import com.guest_platform.entity.AdminUser;
import com.guest_platform.repository.AdminAuditLogRepository;
import com.guest_platform.repository.AdminSessionRepository;
import com.guest_platform.repository.AdminUserRepository;
import com.guest_platform.service.AdminAuditService;
import com.guest_platform.service.AdminBootstrapService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.admin.bootstrap.email=owner-admin@hostvero.net",
        "app.admin.bootstrap.password=AdminStrongPass!123",
        "app.admin.bootstrap.name=Hostvero Owner",
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.admin-login.max-requests=5",
        "app.security.rate-limit.admin-login.window-seconds=900"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {
    private static final String ADMIN_EMAIL = "owner-admin@hostvero.net";
    private static final String ADMIN_PASSWORD = "AdminStrongPass!123";
    private static final String ADMIN_COOKIE = "HOSTVERO_ADMIN_SESSION";
    private static final String ADMIN_ORIGIN = "http://localhost:8080";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(10);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AdminUserRepository admins;
    @Autowired AdminSessionRepository sessions;
    @Autowired AdminAuditLogRepository audits;
    @Autowired AdminBootstrapService bootstrap;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void bootstrapCreatesOneHashedSuperAdminAndIsIdempotent() throws Exception {
        assertThat(admins.count()).isGreaterThanOrEqualTo(1);
        AdminUser admin = admins.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo(ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
        long before = admins.count();
        bootstrap.run(null);
        assertThat(admins.count()).isEqualTo(before);
        assertThat(audits.findAllByOrderByCreatedAtAsc()).anyMatch(log ->
                AdminAuditService.ADMIN_BOOTSTRAPPED.equals(log.getAction()));
    }

    @Test
    void loginUsesSeparateSecureServerSessionAndSafeResponse() throws Exception {
        MvcResult result = login(ADMIN_EMAIL, ADMIN_PASSWORD, uniqueIp()).andExpect(status().isOk())
                .andExpect(cookie().exists(ADMIN_COOKIE))
                .andExpect(cookie().httpOnly(ADMIN_COOKIE, true))
                .andExpect(cookie().sameSite(ADMIN_COOKIE, "Strict"))
                .andExpect(cookie().path(ADMIN_COOKIE, "/"))
                .andExpect(cookie().doesNotExist("HOSTVERO_SESSION"))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        String rawToken = cookieValue(result, ADMIN_COOKIE);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawToken);
        assertThat(sessions.findAll()).anyMatch(session -> !session.getTokenHash().equals(rawToken));
        assertThat(audits.findAllByOrderByCreatedAtAsc()).anyMatch(log ->
                AdminAuditService.ADMIN_LOGIN_SUCCESS.equals(log.getAction()));
    }

    @Test
    void hostSessionCannotAuthorizeAdminButValidAdminCookieCan() throws Exception {
        String hostCookie = registerHostCookie();
        mockMvc.perform(get("/api/admin/me").cookie(new jakarta.servlet.http.Cookie("HOSTVERO_SESSION", hostCookie)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_AUTH_REQUIRED"));

        MvcResult login = login(ADMIN_EMAIL, ADMIN_PASSWORD, uniqueIp()).andExpect(status().isOk()).andReturn();
        mockMvc.perform(get("/api/admin/me").cookie(adminCookie(login)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Hostvero Owner"));
    }

    @Test
    void invalidAndExpiredAdminCookiesAreRejected() throws Exception {
        mockMvc.perform(get("/api/admin/me").cookie(new jakarta.servlet.http.Cookie(ADMIN_COOKIE, "invalid")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_AUTH_REQUIRED"));

        AdminUser admin = admins.findByEmail(ADMIN_EMAIL).orElseThrow();
        String raw = "expired-admin-token";
        sessions.save(new AdminSession(admin, sha256(raw), Instant.now().minusSeconds(1)));
        mockMvc.perform(get("/api/admin/me").cookie(new jakarta.servlet.http.Cookie(ADMIN_COOKIE, raw)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_SESSION_EXPIRED"));
    }

    @Test
    void logoutRequiresAllowedOriginRevokesSessionAndAuditsWithoutSecrets() throws Exception {
        MvcResult login = login(ADMIN_EMAIL, ADMIN_PASSWORD, uniqueIp()).andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie cookie = adminCookie(login);
        long logoutAuditsBeforeRejection = logoutAuditCount();
        mockMvc.perform(post("/api/admin/auth/logout").header(HttpHeaders.ORIGIN, "https://evil.example").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").doesNotExist())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.validationErrors").doesNotExist());
        mockMvc.perform(get("/api/admin/me").cookie(cookie)).andExpect(status().isOk());
        assertThat(logoutAuditCount()).isEqualTo(logoutAuditsBeforeRejection);
        mockMvc.perform(post("/api/admin/auth/logout").header(HttpHeaders.ORIGIN, ADMIN_ORIGIN).cookie(cookie))
                .andExpect(status().isNoContent()).andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        mockMvc.perform(get("/api/admin/me").cookie(cookie)).andExpect(status().isUnauthorized());
        var auditRows = audits.findAllByOrderByCreatedAtAsc();
        assertThat(auditRows).anyMatch(log -> AdminAuditService.ADMIN_LOGOUT.equals(log.getAction()));
        assertThat(auditRows).filteredOn(log -> java.util.Set.of(AdminAuditService.ADMIN_BOOTSTRAPPED,
                AdminAuditService.ADMIN_LOGIN_SUCCESS, AdminAuditService.ADMIN_LOGOUT).contains(log.getAction()))
                .allSatisfy(log -> {
            assertThat(log.getPreviousState()).isNull();
            assertThat(log.getNewState()).isNull();
            assertThat(log.getReason()).isNull();
            assertThat(log.getMetadataJson()).isNull();
        });
    }

    @Test
    void wrongEmailAndPasswordShareSafeResponseAndDisabledAdminCannotLogin() throws Exception {
        String wrongEmail = login("missing-admin@hostvero.net", "WrongPassword!123", uniqueIp())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = login(ADMIN_EMAIL, "WrongPassword!123", uniqueIp())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(wrongEmail).isEqualTo(wrongPassword);

        AdminUser disabled = admins.save(new AdminUser("disabled-admin@hostvero.net",
                passwordEncoder.encode(ADMIN_PASSWORD), "Disabled Admin", AdminRole.SUPPORT));
        disabled.disable();
        admins.saveAndFlush(disabled);
        login(disabled.getEmail(), ADMIN_PASSWORD, uniqueIp()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_DISABLED"));
    }

    @Test
    void adminLoginIsRateLimitedByIp() throws Exception {
        String ip = "10.20.30.40";
        for (int attempt = 0; attempt < 5; attempt++) {
            login("rate-limit@hostvero.net", "WrongPassword!123", ip).andExpect(status().isUnauthorized());
        }
        login("rate-limit@hostvero.net", "WrongPassword!123", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login").with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String registerHostCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").with(request -> {
                    request.setRemoteAddr(uniqueIp()); return request;
                }).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "email", "admin-boundary-" + System.nanoTime() + "@example.com",
                        "password", "StrongPass!123", "passwordConfirmation", "StrongPass!123",
                        "fullName", "Normal Host", "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn();
        return cookieValue(result, "HOSTVERO_SESSION");
    }

    private jakarta.servlet.http.Cookie adminCookie(MvcResult result) {
        return new jakarta.servlet.http.Cookie(ADMIN_COOKIE, cookieValue(result, ADMIN_COOKIE));
    }
    private String cookieValue(MvcResult result, String name) {
        return java.util.Arrays.stream(result.getResponse().getCookies()).filter(cookie -> name.equals(cookie.getName()))
                .findFirst().orElseThrow().getValue();
    }
    private String uniqueIp() { return "10.0.0." + IP_SEQUENCE.incrementAndGet(); }
    private long logoutAuditCount() {
        return audits.findAllByOrderByCreatedAtAsc().stream()
                .filter(log -> AdminAuditService.ADMIN_LOGOUT.equals(log.getAction())).count();
    }
    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
