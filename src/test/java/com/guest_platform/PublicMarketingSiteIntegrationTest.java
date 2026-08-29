package com.guest_platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.admin.public-base-url=https://admin.hostvero.net")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicMarketingSiteIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void hostnameRoutingKeepsPublicHostAndAdminSurfacesSeparate() throws Exception {
        mvc.perform(get("/").header("Host","hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/site/index.html"));
        mvc.perform(get("/").header("Host","www.hostvero.net"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(header().string("Location", "https://hostvero.net/"));
        mvc.perform(get("/").header("Host","app.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/").header("Host","admin.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/admin/index.html"));
        mvc.perform(get("/robots.txt").header("Host","hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/site/public-robots.txt"));
        mvc.perform(get("/robots.txt").header("Host","app.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/site/private-robots.txt"));
    }

    @Test void wwwPublicPagesPermanentlyRedirectWithoutAffectingAppOrAdminHosts() throws Exception {
        mvc.perform(get("/for-hosts").queryParam("source", "search").header("Host", "www.hostvero.net"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(header().string("Location", "https://hostvero.net/for-hosts?source=search"));
        mvc.perform(get("/").header("Host", "app.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/").header("Host", "admin.hostvero.net"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/admin/index.html"));
    }

    @Test void indexablePagesHaveUniqueCompleteCanonicalSeoMetadata() throws Exception {
        String[] pages = {"index", "for-hosts", "for-guests", "pricing", "safety", "contact", "host-agreement"};
        String[] canonicals = {"https://hostvero.net/", "https://hostvero.net/for-hosts",
                "https://hostvero.net/for-guests", "https://hostvero.net/pricing", "https://hostvero.net/safety",
                "https://hostvero.net/contact", "https://hostvero.net/host-agreement"};
        Set<String> titles = new HashSet<>();
        for (int index = 0; index < pages.length; index++) {
            String html = pageHtml(pages[index]);
            assertEquals(1, occurrences(html, "<title>"));
            assertEquals(1, occurrences(html, "<meta name=\"description\""));
            assertEquals(1, occurrences(html, "<link rel=\"canonical\""));
            assertTrue(html.contains("<link rel=\"canonical\" href=\"" + canonicals[index] + "\">"));
            assertFalse(html.contains("rel=\"canonical\" href=\"https://www."));
            assertTrue(html.contains("<meta name=\"robots\" content=\"index,follow\">"));
            assertEquals(1, occurrences(html, "<h1"));
            assertTrue(titles.add(extract(html, "<title>(.*?)</title>")));
            for (String name : new String[]{"og:title", "og:description", "og:type", "og:url", "og:image"}) {
                assertEquals(1, occurrences(html, "<meta property=\"" + name + "\""));
            }
            for (String name : new String[]{"twitter:card", "twitter:title", "twitter:description", "twitter:image"}) {
                assertEquals(1, occurrences(html, "<meta name=\"" + name + "\""));
            }
            assertTrue(html.contains("<meta property=\"og:url\" content=\"" + canonicals[index] + "\">"));
        }
    }

    @Test void homepageSchemaIsValidAndContainsNoFabricatedOrganizationClaims() throws Exception {
        String html = pageHtml("index");
        String schema = extract(html, "<script type=\"application/ld\\+json\">(.*?)</script>");
        new ObjectMapper().readTree(schema);
        assertTrue(schema.contains("\"@type\":\"Organization\""));
        assertTrue(schema.contains("\"@type\":\"WebSite\""));
        assertTrue(schema.contains("\"email\":\"support@hostvero.net\""));
        assertFalse(schema.contains("telephone"));
        assertFalse(schema.contains("address"));
        assertFalse(schema.contains("rating"));
        assertFalse(schema.contains("reviewCount"));
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

    @Test void gtmIsLimitedToPublicMarketingHtml() throws Exception {
        for (String page : new String[]{"index", "for-hosts", "for-guests", "pricing", "safety", "contact",
                "privacy", "terms", "host-agreement"}) {
            mvc.perform(get("/site/" + page + ".html")).andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "<head><!-- Google Tag Manager --><script src=\"/site/gtm-loader.js\" defer>")))
                    .andExpect(content().string(containsString(
                            "<body><!-- Google Tag Manager (noscript) --><noscript><iframe")))
                    .andExpect(content().string(containsString(
                            "https://www.googletagmanager.com/ns.html?id=GTM-5349TBPM")));
        }

        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("GTM-5349TBPM"))));
        mvc.perform(get("/admin/index.html")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("GTM-5349TBPM"))));
    }

    @Test void marketingCspAllowsOnlyRequiredGtmAndGa4Origins() throws Exception {
        for (String path : new String[]{"/", "/for-hosts", "/for-guests", "/pricing", "/safety", "/contact",
                "/host-agreement", "/site/site.css", "/site/gtm-loader.js"}) {
            mvc.perform(get(path).header("Host", "hostvero.net"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", containsString(
                            "script-src 'self' https://www.googletagmanager.com")))
                    .andExpect(header().string("Content-Security-Policy", containsString(
                            "connect-src 'self' https://www.google-analytics.com https://region1.google-analytics.com https://www.googletagmanager.com")))
                    .andExpect(header().string("Content-Security-Policy", containsString(
                            "img-src 'self' data: https://www.google-analytics.com https://www.googletagmanager.com")))
                    .andExpect(header().string("Content-Security-Policy", containsString(
                            "frame-src https://www.googletagmanager.com")))
                    .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-eval"))))
                    .andExpect(header().string("Content-Security-Policy", not(containsString(
                            "script-src 'self' 'unsafe-inline'"))))
                    .andExpect(header().string("Content-Security-Policy", not(containsString("*.google"))))
                    .andExpect(result -> assertEquals(1,
                            result.getResponse().getHeaders("Content-Security-Policy").size()));
        }

        for (String[] request : new String[][]{{"/", "app.hostvero.net"}, {"/", "admin.hostvero.net"},
                {"/api/health", "hostvero.net"}}) {
            mvc.perform(get(request[0]).header("Host", request[1]))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self';")))
                    .andExpect(header().string("Content-Security-Policy", not(containsString("googletagmanager"))))
                    .andExpect(result -> assertEquals(1,
                            result.getResponse().getHeaders("Content-Security-Policy").size()));
        }

        mvc.perform(get("/site/gtm-loader.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("GTM-5349TBPM")))
                .andExpect(content().string(not(containsString("G-EMTK13GWYP"))));
    }

    @Test void publicAndLegalRoutesResolve() throws Exception {
        for(String route : new String[]{"/for-hosts","/for-guests","/pricing","/safety","/contact","/privacy","/terms","/host-agreement"}) {
            mvc.perform(get(route).header("Host","hostvero.net"))
                    .andExpect(status().isOk()).andExpect(forwardedUrl("/site"+route+".html"));
        }
        mvc.perform(get("/site/pricing.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("5%")));
    }

    @Test void verifiedPublicSupportEmailIsPublishedWithoutPlaceholderOrPhone() throws Exception {
        mvc.perform(get("/site/contact.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("support@hostvero.net")))
                .andExpect(content().string(containsString("mailto:support@hostvero.net")))
                .andExpect(content().string(not(containsString(
                        "Hostvero support contact details will be available before public launch."))))
                .andExpect(content().string(not(containsString("tel:"))))
                .andExpect(content().string(not(containsString("Phone"))));
    }

    @Test void draftLegalPagesAreNotIndexedOrPublishedInSitemap() throws Exception {
        mvc.perform(get("/site/privacy.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Legal review required before launch.")))
                .andExpect(content().string(containsString("noindex,nofollow")));
        mvc.perform(get("/site/terms.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Legal review required before launch.")))
                .andExpect(content().string(containsString("noindex,nofollow")));
        mvc.perform(get("/site/sitemap.xml")).andExpect(status().isOk())
                .andExpect(content().string(containsString("https://hostvero.net/</loc>")))
                .andExpect(content().string(containsString("https://hostvero.net/for-hosts</loc>")))
                .andExpect(content().string(containsString("https://hostvero.net/for-guests</loc>")))
                .andExpect(content().string(containsString("https://hostvero.net/pricing</loc>")))
                .andExpect(content().string(containsString("https://hostvero.net/safety</loc>")))
                .andExpect(content().string(containsString("https://hostvero.net/contact</loc>")))
                .andExpect(content().string(containsString("https://hostvero.net/host-agreement</loc>")))
                .andExpect(content().string(not(containsString("https://hostvero.net/privacy"))))
                .andExpect(content().string(not(containsString("https://hostvero.net/terms"))))
                .andExpect(content().string(not(containsString("https://www.hostvero.net"))))
                .andExpect(result -> assertEquals(7,
                        occurrences(result.getResponse().getContentAsString(), "<loc>")));
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

    @Test void privateHostAndAdminHtmlContainNoPublicSeoMetadata() throws Exception {
        for (String path : new String[]{"/index.html", "/admin/index.html"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("https://hostvero.net/images/hostvero-logo-clean.png"))))
                    .andExpect(content().string(not(containsString("application/ld+json"))))
                    .andExpect(content().string(not(containsString("og:url"))));
        }
    }

    private String pageHtml(String page) throws Exception {
        MvcResult result = mvc.perform(get("/site/" + page + ".html")).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString();
    }

    private static int occurrences(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }

    private static String extract(String value, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(value);
        assertTrue(matcher.find(), "Missing expected metadata: " + regex);
        return matcher.group(1);
    }
}
