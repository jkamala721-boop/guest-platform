package com.guest_platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.admin.public-base-url=https://admin.hostvero.net")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicMarketingSiteIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void hostnameRoutingKeepsPublicHostAndAdminSurfacesSeparate() throws Exception {
        mvc.perform(get("/").header("Host","hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/site/index.html"));
        mvc.perform(get("/").header("Host","www.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/site/index.html"));
        mvc.perform(get("/").header("Host","app.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/").header("Host","admin.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/admin/index.html"));
        mvc.perform(get("/robots.txt").header("Host","hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/site/public-robots.txt"));
        mvc.perform(get("/robots.txt").header("Host","app.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/site/private-robots.txt"));
    }

    @Test void publicHomeHasAppCtasMobileNavigationAndNoInternalData() throws Exception {
        mvc.perform(get("/site/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://app.hostvero.net/")))
                .andExpect(content().string(containsString("data-menu-button")))
                .andExpect(content().string(containsString("Hostvero")))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("tokenHash"))))
                .andExpect(content().string(not(containsString("recipientCode"))))
                .andExpect(content().string(not(containsString("/api/admin"))));
    }

    @Test void publicAndLegalRoutesResolve() throws Exception {
        for(String route : new String[]{"/for-hosts","/for-guests","/pricing","/safety","/contact","/privacy","/terms","/host-agreement"}) {
            mvc.perform(get(route).header("Host","hostvero.net"))
                    .andExpect(status().isOk()).andExpect(forwardedUrl("/site"+route+".html"));
        }
        mvc.perform(get("/site/pricing.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("5%")));
    }

    @Test void draftLegalPagesAreNotIndexedOrPublishedInSitemap() throws Exception {
        mvc.perform(get("/site/privacy.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Legal review required before launch.")))
                .andExpect(content().string(containsString("noindex,nofollow")));
        mvc.perform(get("/site/terms.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Legal review required before launch.")))
                .andExpect(content().string(containsString("noindex,nofollow")));
        mvc.perform(get("/site/sitemap.xml")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("https://hostvero.net/privacy"))))
                .andExpect(content().string(not(containsString("https://hostvero.net/terms"))));
    }

    @Test void publicContentContainsNoDraftAgreementFilesPlaceholderDomainsOrSecrets() throws Exception {
        for (String page : new String[]{"index", "for-hosts", "for-guests", "pricing", "safety", "contact",
                "privacy", "terms", "host-agreement"}) {
            mvc.perform(get("/site/" + page + ".html")).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("example.com"))))
                    .andExpect(content().string(not(containsString("agreement-request.json"))))
                    .andExpect(content().string(not(containsString("host-agreement-v1.txt"))))
                    .andExpect(content().string(not(containsString("host-agreement-v1-1.txt"))))
                    .andExpect(content().string(not(containsString("SUPABASE_SERVICE"))))
                    .andExpect(content().string(not(containsString("RESEND_API_KEY"))))
                    .andExpect(content().string(not(containsString("PAYSTACK_SECRET"))))
                    .andExpect(content().string(not(containsString("passwordHash"))))
                    .andExpect(content().string(not(containsString("tokenHash"))));
        }
        mvc.perform(get("/site/host-agreement.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("This public page is informational only.")))
                .andExpect(content().string(containsString("in-app version governs acceptance")));
    }
}
