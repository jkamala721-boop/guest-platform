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
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("tokenHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Refresh this page in a few moments")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("receipt/document")));
        mvc.perform(get("/js/api.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("credentials: 'same-origin'")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hostvero.session-token"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("localStorage"))));
    }

    @Test
    void invalidPublicTokenUsesTheExistingSafeApiBehavior() throws Exception {
        mvc.perform(get("/api/public/guest/not-a-valid-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void hostOnboardingJourneyIsPresentWithoutEmbeddingSensitiveState() throws Exception {
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/me/onboarding")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/me/verification")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/me/agreement/accept")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add your first property")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Set up payouts")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your Hostvero account setup is complete.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verification required within")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verification submitted")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("review your information")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verification under review")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verification needs attention")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Continue verification")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resubmit verification")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("verificationDaysRemaining")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("access.verificationStatus==='VERIFIED'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("active === 'onboarding' ? '' : verificationReminder()")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"#/onboarding\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HOST_VERIFICATION_REQUIRED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mobile-menu-button")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Log out")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("idFingerprint"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("recipientCode"))));
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
