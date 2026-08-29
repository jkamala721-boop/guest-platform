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
    void bookingCheckinLocationFieldsUseExistingFormsAndGuestSafeRendering() throws Exception {
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"houseNumber\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"blockName\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("maxlength=\"100\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("House / Unit number")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Block / Building section")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("result.stay.houseNumber")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("property.blockName")));
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
    void globalCountrySelectorAndHostOnlyPwaContractsArePresent() throws Exception {
        mvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\": \"Hostvero\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"start_url\": \"/#/overview\"")));
        mvc.perform(get("/service-worker.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("url.pathname.startsWith('/api/')")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cache: 'no-store'")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("recipientCode"))));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/manifest.webmanifest")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("apple-mobile-web-app-capable")));
        mvc.perform(get("/admin/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/manifest.webmanifest"))));
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/reference/countries")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"combobox\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No countries found")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Install Hostvero")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add to Home Screen")));
    }

    @Test
    void guidedMobileVerificationFlowUsesAuthoritativeCountrySelection() throws Exception {
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose document country")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Search countries")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose your document country first.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("document-type-card")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Passport verification is currently available")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your phone can be from a different country")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review and submit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("last4=raw.slice(-4)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("escapeHtml(last4)")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("escapeHtml(raw)"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("filterCountries")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("country.code.toLocaleLowerCase().includes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("countryValue.value=country.code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("if(!$('#verification-country').value)")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("idFingerprint"))));
        mvc.perform(get("/service-worker.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("url.pathname.startsWith('/api/')")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cache: 'no-store'")));
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
