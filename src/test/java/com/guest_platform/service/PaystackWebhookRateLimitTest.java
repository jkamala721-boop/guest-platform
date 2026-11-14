package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.guest_platform.config.PublicRateLimitProperties;
import com.guest_platform.exception.WebhookAuthenticationException;
import com.guest_platform.security.PublicRateLimiter;
import com.guest_platform.security.RateLimitExceededException;
import com.guest_platform.service.payment.PaystackApiClient;

import tools.jackson.databind.ObjectMapper;

class PaystackWebhookRateLimitTest {
    private static final String SECRET = "paystack-rate-limit-test-secret";
    private static final String NO_OP_PAYLOAD = "{\"event\":\"unhandled.event\"}";

    @Test
    void invalidSignaturesDoNotConsumeTheAuthenticated240PerMinuteEventBucket() throws Exception {
        PublicRateLimitProperties properties = new PublicRateLimitProperties();
        assertThat(properties.getPaystackWebhook().getMaxRequests()).isEqualTo(240);
        assertThat(properties.getPaystackWebhook().getWindowSeconds()).isEqualTo(60);
        HostPayoutExecutionService payouts = mock(HostPayoutExecutionService.class);
        PaystackWebhookService service = new PaystackWebhookService("mock", SECRET, new ObjectMapper(),
                mock(PaystackApiClient.class), mock(PaymentService.class), payouts,
                new PublicRateLimiter(properties));

        assertThatThrownBy(() -> service.process("invalid-signature", NO_OP_PAYLOAD))
                .isInstanceOf(WebhookAuthenticationException.class);

        String signature = sign(NO_OP_PAYLOAD);
        for (int request = 0; request < 240; request++) {
            service.process(signature, NO_OP_PAYLOAD);
        }

        String transferPayload = "{\"event\":\"transfer.success\",\"data\":{\"reference\":\"ref-1\"}}";
        assertThatThrownBy(() -> service.process(sign(transferPayload), transferPayload))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(exception -> assertThat(((RateLimitExceededException) exception)
                        .getRetryAfterSeconds()).isPositive());
        verify(payouts, never()).processVerifiedTransferWebhook(anyString(), anyString(), any());
    }

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
