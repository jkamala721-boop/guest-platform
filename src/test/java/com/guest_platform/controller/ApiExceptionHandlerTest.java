package com.guest_platform.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
