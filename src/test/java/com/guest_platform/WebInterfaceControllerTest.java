package com.guest_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebInterfaceControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void appShellAndPublicGuestRouteAreAvailableWithoutHostAuthentication() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hostvero")));
        mvc.perform(get("/guest/non-sensitive-placeholder"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void staticInterfaceDoesNotMakeHostDataPublic() throws Exception {
        mvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("tokenHash"))));
        mvc.perform(get("/js/api.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sessionStorage")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("localStorage"))));
    }

    @Test
    void invalidPublicTokenUsesTheExistingSafeApiBehavior() throws Exception {
        mvc.perform(get("/api/public/guest/not-a-valid-token"))
                .andExpect(status().isNotFound());
    }
    @Test
void propertyCreationRouteIsExplicitAndNavigationBreakpointIsMobileOnly() throws Exception {
    mvc.perform(get("/js/app.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                    "if (route === 'properties/new') return renderPropertyForm(null);")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                    "return renderPropertyForm(propertyId || null);"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                    "await put(")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                    "await post(")));

    mvc.perform(get("/css/layout.css"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("@media (max-width: 767px)")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                    "@media (max-width: 880px)"))));
} }