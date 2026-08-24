package com.guest_platform.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PaystackPaymentProviderTest {

    @Test
    void convertsKesAmountsToLowestCurrencyUnitWithoutRounding() {
        assertThat(PaystackPaymentProvider.toMinorUnits(new BigDecimal("3675.00"))).isEqualTo(367500L);
    }

    @Test
    void bankCheckoutKeepsTheServerCalculatedFlatChargeAndMainAccountAsBearer() {
        CapturingClient client = new CapturingClient();
        PaystackPaymentProvider provider = new PaystackPaymentProvider("live", "test-secret", client);
        UUID paymentId = UUID.randomUUID();

        provider.initiate(new PaymentProviderAdapter.PaymentInitiationRequest(paymentId, UUID.randomUUID(),
                new BigDecimal("3675.00"), "KES", "https://example.test/return", "guest@example.com",
                "ACCT_TEST", new BigDecimal("175.00")));

        assertThat(client.request.transaction_charge()).isEqualTo(17500L);
        assertThat(client.request.bearer()).isEqualTo("account");
    }

    private static final class CapturingClient extends PaystackApiClient {
        private InitializeRequest request;

        private CapturingClient() {
            super("test-secret", new tools.jackson.databind.ObjectMapper());
        }

        @Override
        public InitializeResult initialize(InitializeRequest request) {
            this.request = request;
            return new InitializeResult(request.reference(), "https://checkout.paystack.test");
        }
    }
}
