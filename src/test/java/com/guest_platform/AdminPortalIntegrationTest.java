package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:admin-portal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.admin.public-base-url=https://admin.hostvero.net",
        "app.admin.bootstrap.email=portal-admin@hostvero.test",
        "app.admin.bootstrap.password=StrongPortalPass!123",
        "app.admin.bootstrap.name=Portal Admin"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class AdminPortalIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void adminDomainRootAndStaticShellArePublicButAdminDataRemainsProtected() throws Exception {
        mvc.perform(get("/").header("Host","admin.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/admin/index.html"));
        mvc.perform(get("/").header("Host","app.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/admin/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Admin sign in")));
        mvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_AUTH_REQUIRED"));
    }

    @Test void portalLoginUsesDedicatedAdminEndpointAndReturnsSafeIdentity() throws Exception {
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"portal-admin@hostvero.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_INVALID_CREDENTIALS"));
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"portal-admin@hostvero.test\",\"password\":\"StrongPortalPass!123\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("HOSTVERO_ADMIN_SESSION=")))
                .andExpect(jsonPath("$.displayName").value("Portal Admin"))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test void portalUsesCookieCredentialsWithoutPersistentTokenStorageAndContainsLaunchViews() throws Exception {
        String api=resource("static/admin/admin-api.js"),app=resource("static/admin/admin-app.js");
        assertThat(api).contains("credentials:\"include\"").contains("/api/admin/auth/login")
                .contains("/api/admin/hosts").contains("/api/admin/payouts");
        assertThat(api+app).doesNotContain("localStorage").doesNotContain("sessionStorage")
                .doesNotContain("recipientCode").doesNotContain("passwordHash").doesNotContain("sessionToken");
        assertThat(app).contains("Internal notes").contains("Operational timeline")
                .contains("Only confirm after").contains("FINANCE").contains("SUPER_ADMIN")
                .contains("SUPPORT").contains("OPERATIONS");
    }

    private String resource(String path) throws Exception {
        try(var input=new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
