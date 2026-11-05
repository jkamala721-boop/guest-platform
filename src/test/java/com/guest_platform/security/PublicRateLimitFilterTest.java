package com.guest_platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

import com.guest_platform.config.PublicRateLimitProperties;
import com.guest_platform.config.PublicRateLimitProperties.Limit;
import com.guest_platform.repository.GuestLinkRepository;

class PublicRateLimitFilterTest {

    @Test
    void eachSensitivePublicRouteReturnsSafe429AfterItsOwnThreshold() throws Exception {
        PublicRateLimitFilter filter = filterWithOneRequestLimit();

        assertRateLimited(filter, "GET", "/api/public/guest/token-one");
        assertRateLimited(filter, "POST", "/api/public/guest/token-two/email-verification");
        assertRateLimited(filter, "POST", "/api/public/guest/token-three/email-verification/confirm");
        assertRateLimited(filter, "POST", "/api/public/guest/token-four/payments");
        assertRateLimited(filter, "PUT", "/api/public/guest/token-five/registration");
        assertRateLimited(filter, "POST", "/api/public/guest/token-six/extend");
        assertRateLimited(filter, "POST", "/api/public/guest/token-seven/book-again");
        assertRateLimited(filter, "POST", "/api/public/guest/token-eight/extensions/extension-id/payments");
        assertRateLimited(filter, "GET", "/api/public/guest/token-nine/availability");
        assertRateLimited(filter, "GET", "/api/public/guest/token-ten/receipt");
        assertRateLimited(filter, "GET", "/api/public/guest/token-eleven/receipt/document");
    }

    @Test
    void guestTokenIsHashedAndIsolatedWithoutSocketIdentity() throws Exception {
        PublicRateLimitFilter filter = filterWithOneRequestLimit();
        MockHttpServletResponse first = execute(filter, "GET", "/api/public/guest/raw-token-never-stored", null);
        MockHttpServletResponse blocked = execute(filter, "GET", "/api/public/guest/raw-token-never-stored", null);
        MockHttpServletResponse otherLink = execute(filter, "GET", "/api/public/guest/another-link", null);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).doesNotContain("raw-token-never-stored")
                .contains("Too many attempts. Please wait and try again.");
        assertThat(otherLink.getStatus()).isEqualTo(200);
    }

    @Test
    void manyInvalidGuestTokensCannotExhaustLimiterCapacity() throws Exception {
        AtomicBoolean tokenExists = new AtomicBoolean(false);
        GuestLinkRepository guestLinkRepository = guestLinkRepository(tokenExists::get);
        PublicRateLimitFilter filter = filterWithOneRequestLimit(
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC), 1, guestLinkRepository);

        for (int token = 0; token < 100; token++) {
            assertThat(execute(filter, "GET", "/api/public/guest/invalid-" + token, null).getStatus())
                    .isEqualTo(200);
        }

        tokenExists.set(true);
        assertThat(execute(filter, "GET", "/api/public/guest/valid-after-invalid-flood", null).getStatus())
                .isEqualTo(200);
    }

    @Test
    void validGuestTokensStayIsolatedAndExpiredCapacityCanBeReclaimed() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        PublicRateLimitFilter filter = filterWithOneRequestLimit(clock, 1, true);

        assertThat(execute(filter, "GET", "/api/public/guest/valid-a", null).getStatus()).isEqualTo(200);
        assertThat(execute(filter, "GET", "/api/public/guest/valid-a", null).getStatus()).isEqualTo(429);
        assertThat(execute(filter, "GET", "/api/public/guest/valid-b", null).getStatus()).isEqualTo(429);

        clock.advance(Duration.ofHours(1).plusSeconds(1));

        assertThat(execute(filter, "GET", "/api/public/guest/valid-b", null).getStatus()).isEqualTo(200);
        assertThat(execute(filter, "GET", "/api/public/guest/valid-b", null).getStatus()).isEqualTo(429);
    }

    @Test
    void rejectedRequestIncludesTheWindowRetryDuration() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        PublicRateLimitFilter filter = filterWithOneRequestLimit(clock);
        execute(filter, "POST", "/api/public/guest/retry-link/extend", "198.51.100.10");

        MockHttpServletResponse response = execute(filter, "POST", "/api/public/guest/retry-link/extend", "198.51.100.10");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("61");

        clock.advance(Duration.ofSeconds(61));
        assertThat(execute(filter, "POST", "/api/public/guest/retry-link/extend", "198.51.100.10").getStatus())
                .isEqualTo(200);
    }

    @Test
    void missingSocketAndForwardedHeadersDoNotCreateSharedGuestIdentity() throws Exception {
        PublicRateLimitFilter filter = filterWithOneRequestLimit();
        MockHttpServletRequest firstRequest = request("GET", "/api/public/guest/guest-a", null);
        firstRequest.addHeader("X-Forwarded-For", "203.0.113.90");
        firstRequest.addHeader("Forwarded", "for=203.0.113.91;proto=https");
        firstRequest.addHeader("CF-Connecting-IP", "203.0.113.10");
        MockHttpServletResponse first = execute(filter, firstRequest);

        MockHttpServletRequest spoofedRetry = request("GET", "/api/public/guest/guest-a", null);
        spoofedRetry.addHeader("X-Forwarded-For", "192.0.2.55");
        spoofedRetry.addHeader("Forwarded", "for=192.0.2.56");
        MockHttpServletResponse blocked = execute(filter, spoofedRetry);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(execute(filter, "GET", "/api/public/guest/guest-b", null).getStatus()).isEqualTo(200);
    }

    @Test
    void paystackWebhookIsNotLimitedBeforeControllerSignatureVerification() throws Exception {
        PublicRateLimitFilter filter = filterWithOneRequestLimit();

        assertThat(execute(filter, "POST", "/api/webhooks/paystack", "198.51.100.10").getStatus()).isEqualTo(200);
        assertThat(execute(filter, "POST", "/api/webhooks/paystack", "198.51.100.11").getStatus()).isEqualTo(200);
    }

    @Test
    void productionDisablesContainerForwardedHeaderResolution() throws Exception {
        Properties production = new Properties();
        try (var input = new ClassPathResource("application-prod.properties").getInputStream()) {
            production.load(input);
        }

        assertThat(production.getProperty("server.forward-headers-strategy")).isEqualTo("none");
    }

    @Test
    void hostAndPaymentRoutesAreNotAffectedByGuestLinkLimits() throws Exception {
        PublicRateLimitFilter filter = filterWithOneRequestLimit();

        assertThat(execute(filter, "POST", "/api/bookings/booking-id/extend", "198.51.100.10").getStatus()).isEqualTo(200);
        assertThat(execute(filter, "POST", "/api/bookings/booking-id/payments", "198.51.100.10").getStatus()).isEqualTo(200);
        assertThat(execute(filter, "POST", "/api/bookings/booking-id/extend", "198.51.100.10").getStatus()).isEqualTo(200);
        assertThat(execute(filter, "POST", "/api/public/guest/host-isolation/extend", "198.51.100.10").getStatus()).isEqualTo(200);
    }

    private void assertRateLimited(PublicRateLimitFilter filter, String method, String path) throws Exception {
        assertThat(execute(filter, method, path, "198.51.100.10").getStatus()).isEqualTo(200);
        MockHttpServletResponse response = execute(filter, method, path, "198.51.100.10");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
    }

    private MockHttpServletResponse execute(PublicRateLimitFilter filter, String method, String path, String remoteAddress)
            throws Exception {
        return execute(filter, request(method, path, remoteAddress));
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (remoteAddress != null) request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletResponse execute(PublicRateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private PublicRateLimitFilter filterWithOneRequestLimit() {
        return filterWithOneRequestLimit(Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC));
    }

    private PublicRateLimitFilter filterWithOneRequestLimit(Clock clock) {
        return filterWithOneRequestLimit(clock, 100, true);
    }

    private PublicRateLimitFilter filterWithOneRequestLimit(Clock clock, int maxBuckets, boolean allTokensValid) {
        return filterWithOneRequestLimit(clock, maxBuckets, guestLinkRepository(() -> allTokensValid));
    }

    private PublicRateLimitFilter filterWithOneRequestLimit(Clock clock, int maxBuckets,
            GuestLinkRepository guestLinkRepository) {
        PublicRateLimitProperties properties = new PublicRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxBuckets(maxBuckets);
        Limit oneRequest = new Limit(1, 60);
        properties.setLogin(oneRequest);
        properties.setAdminLogin(new Limit(1, 60));
        properties.setRegistration(new Limit(1, 60));
        properties.setGuestLink(new Limit(1, 60));
        properties.setGuestRegistration(new Limit(1, 60));
        properties.setGuestBookingChange(new Limit(1, 60));
        properties.setOtpRequest(new Limit(1, 60));
        properties.setOtpVerify(new Limit(1, 60));
        properties.setPaymentInitialization(new Limit(1, 60));
        properties.setPaystackWebhook(new Limit(1, 60));
        return new PublicRateLimitFilter(new PublicRateLimiter(properties, clock),
                new ApiErrorWriter(new ObjectMapper()), guestLinkRepository);
    }

    private GuestLinkRepository guestLinkRepository(BooleanSupplier tokenExists) {
        return (GuestLinkRepository) Proxy.newProxyInstance(GuestLinkRepository.class.getClassLoader(),
                new Class<?>[] { GuestLinkRepository.class }, (proxy, method, arguments) -> {
                    if ("existsByTokenHash".equals(method.getName())) {
                        return tokenExists.getAsBoolean();
                    }
                    if ("toString".equals(method.getName())) return "GuestLinkRepository test double";
                    return null;
                });
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
