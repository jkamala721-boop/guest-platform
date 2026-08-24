package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.guest_platform.entity.Host;
import com.guest_platform.service.payment.PaystackApiClient;

import tools.jackson.databind.ObjectMapper;

class PaystackTransferRecipientServiceTest {

    @Test
    void individualKenyanMpesaRecipientUsesPaystackLocalPhoneRepresentation() {
        CapturingClient client = new CapturingClient();
        PaystackTransferRecipientService service = new PaystackTransferRecipientService("live", "test-secret", client);

        String recipient = service.createIndividualMpesaRecipient(
                new Host("host@example.com", "hash", "Payout Host", "+254711111111"), "+254712345678");

        assertThat(recipient).isEqualTo("RCP_TEST");
        assertThat(client.request.type()).isEqualTo("mobile_money");
        assertThat(client.request.bank_code()).isEqualTo("MPESA");
        assertThat(client.request.currency()).isEqualTo("KES");
        assertThat(client.request.account_number()).isEqualTo("0712345678");
    }

    @Test
    void providerRejectionBecomesSafeInvalidDestinationError() {
        PaystackTransferRecipientService service = new PaystackTransferRecipientService("live", "test-secret",
                new RejectingClient());

        assertThatThrownBy(() -> service.createIndividualMpesaRecipient(
                new Host("host@example.com", "hash", "Payout Host", "+254711111111"), "+254712345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Paystack rejected the M-Pesa payout destination. Check the number and try again.");
    }

    private static final class CapturingClient extends PaystackApiClient {
        private TransferRecipientRequest request;

        private CapturingClient() {
            super("test-secret", new ObjectMapper());
        }

        @Override
        public String createTransferRecipient(TransferRecipientRequest request) {
            this.request = request;
            return "RCP_TEST";
        }
    }

    private static final class RejectingClient extends PaystackApiClient {
        private RejectingClient() {
            super("test-secret", new ObjectMapper());
        }

        @Override
        public String createTransferRecipient(TransferRecipientRequest request) {
            throw new PaystackRequestRejectedException(400, "Account number [redacted] is invalid");
        }
    }
}
