package com.guest_platform.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaystackApiClientTest {

    @Test
    void rejectedProviderMessageKeepsUsefulTextButRedactsPhoneNumbers() {
        String message = PaystackApiClient.safeProviderMessage(
                "{\"status\":false,\"message\":\"Account +254712345678 is invalid\"}");

        assertThat(message).isEqualTo("Account [redacted] is invalid");
    }

    @Test
    void unstructuredRejectedProviderResponseDoesNotReachLogsOrApi() {
        assertThat(PaystackApiClient.safeProviderMessage("<html>gateway failure</html>"))
                .isEqualTo("Paystack rejected the request");
    }
}
