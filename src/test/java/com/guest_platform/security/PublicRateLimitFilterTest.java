package com.guest_platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

import com.guest_platform.config.PublicRateLimitProperties;
import com.guest_platform.config.PublicRateLimitProperties.Limit;

class PublicRateLimitFilterTest {

    @Test
    void eachSensitivePublicRouteReturnsSafe429AfterItsOwnThreshold() throws Exception {
        PublicRateLimitFilter filter = filterWithOneRequestLimit();

        assertRateLimited(filter, "POST", "/api/auth/login");
        assertRateLimited(filter, "POST", "/api/auth/register");
        assertRateLimited(filter, "GET", "/api/public/guest/token-one");
        assertRateLimited(filter, "POST", "/api/public/guest/token-two/email-verification");
        assertRateLimited(filter, "POST", "/api/public/guest/token-three/email-verification/confirm");
        assertRateLimited(filter, "POST", "/api/public/guest/token-four/payments");
        assertRateLimited(filter, "POST", "/api/webhooks/paystack");
    }

    @Test
    void guestTokenIsHashedInTheRateLimitKeyAndDifferentIpsAreIndependent() throws Exception {
        PublicRateLimitFilter filter = filterWithOneRequestLimit();
        MockHttpServletResponse first = execute(filter, "GET", "/api/public/guest/raw-token-never-stored", "198.51.100.10");
        MockHttpServletResponse blocked = execute(filter, "GET", "/api/public/guest/raw-token-never-stored", "198.51.100.10");
        MockHttpServletResponse otherIp = execute(filter, "GET", "/api/public/guest/raw-token-never-stored", "198.51.100.11");

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).doesNotContain("raw-token-never-stored")
                .contains("Too many attempts. Please wait and try again.");
        assertThat(otherIp.getStatus()).isEqualTo(200);
    }

    private void assertRateLimited(PublicRateLimitFilter filter, String method, String path) throws Exception {
        assertThat(execute(filter, method, path, "198.51.100.10").getStatus()).isEqualTo(200);
        MockHttpServletResponse response = execute(filter, method, path, "198.51.100.10");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
    }

    private MockHttpServletResponse execute(PublicRateLimitFilter filter, String method, String path, String remoteAddress)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private PublicRateLimitFilter filterWithOneRequestLimit() {
        PublicRateLimitProperties properties = new PublicRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxBuckets(100);
        Limit oneRequest = new Limit(1, 60);
        properties.setLogin(oneRequest);
        properties.setRegistration(new Limit(1, 60));
        properties.setGuestLink(new Limit(1, 60));
        properties.setOtpRequest(new Limit(1, 60));
        properties.setOtpVerify(new Limit(1, 60));
        properties.setPaymentInitialization(new Limit(1, 60));
        properties.setPaystackWebhook(new Limit(1, 60));
        return new PublicRateLimitFilter(new PublicRateLimiter(properties,
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)),
                new ApiErrorWriter(new ObjectMapper()));
    }
}
