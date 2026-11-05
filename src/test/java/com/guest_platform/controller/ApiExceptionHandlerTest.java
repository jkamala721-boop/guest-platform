package com.guest_platform.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.guest_platform.security.RateLimitExceededException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsProviderPaymentFailureWithoutLeakingInternalDetails() {
        var response = handler.unavailableProvider(new IllegalStateException("Paystack payment was not successful"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_FAILED");
        assertThat(response.getBody().message()).doesNotContain("Paystack");
    }

    @Test
    void mapsProviderUnavailabilityToRetryableSafeError() {
        var response = handler.unavailableProvider(new IllegalStateException("Unable to reach Paystack"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_PROVIDER_UNAVAILABLE");
        assertThat(response.getBody().retryable()).isTrue();
    }

    @Test
    void genericFailureDoesNotExposeAnExceptionMessage() {
        var response = handler.internal(new IllegalStateException("jdbc password=secret"));

        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void mapsRateLimitExceptionToTheExistingRetryable429Response() {
        var response = handler.rateLimited(new RateLimitExceededException(37));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("37");
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().message()).isEqualTo("Too many attempts. Please wait and try again.");
        assertThat(response.getBody().retryable()).isTrue();
    }
}
