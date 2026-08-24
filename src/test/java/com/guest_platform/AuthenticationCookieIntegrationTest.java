package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.entity.HostSession;
import com.guest_platform.repository.HostRepository;
import com.guest_platform.repository.HostSessionRepository;
import com.guest_platform.service.HostSessionService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationCookieIntegrationTest {
    private static final String PASSWORD = "StrongPass!123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HostRepository hostRepository;
    @Autowired private HostSessionRepository hostSessionRepository;

    @Test
    void loginSetsPersistentHttpOnlyCookieWithoutReturningTheOpaqueSessionToken() throws Exception {
        MvcResult registration = register("cookie-host@example.com");

        String cookie = registration.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HOSTVERO_SESSION=", "Path=/", "Max-Age=2592000", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Secure");
        assertThat(registration.getResponse().getContentAsString()).doesNotContain("accessToken");
    }

    @Test
    void cookieAuthenticatesASeparateRequestAndLogoutRevokesAndClearsIt() throws Exception {
        String token = TestSessionTokens.from(register("cookie-reload@example.com"));
        Cookie sessionCookie = new Cookie("HOSTVERO_SESSION", token);

        mockMvc.perform(get("/api/me").cookie(sessionCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie).header(HttpHeaders.ORIGIN, "http://localhost:8080"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(get("/api/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_EXPIRED"));
    }

    @Test
    void missingInvalidCredentialsAndExpiredSessionsUseDistinctSafeErrors() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Please sign in to continue."));

        register("error-host@example.com");
        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "error-host@example.com", "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn();
        MvcResult unknownAccount = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "unknown-host@example.com", "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn();
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownAccount.getResponse().getContentAsString());

        var host = hostRepository.findByEmailIgnoreCase("error-host@example.com").orElseThrow();
        String expiredToken = "expired-cookie-token";
        hostSessionRepository.save(new HostSession(host, sha256(expiredToken), Instant.now().minusSeconds(60)));
        mockMvc.perform(get("/api/me").cookie(new Cookie("HOSTVERO_SESSION", expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Your session has expired. Please sign in again."));
    }

    @Test
    void productionCookieConfigurationMarksTheSessionCookieSecure() {
        HostSessionService service = new HostSessionService(mock(HostSessionRepository.class), 720,
                "HOSTVERO_SESSION", true, "Lax");
        assertThat(service.sessionCookie(new HostSessionService.SessionToken("opaque", Instant.now().plusSeconds(60))).isSecure())
                .isTrue();
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD, "passwordConfirmation", PASSWORD,
                                "fullName", "Cookie Host", "phone", "+254711111111"))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
