package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.guest_platform.dto.HostPayoutSettingsUpsertRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.PayoutMethod;
import com.guest_platform.service.payment.PaystackApiClient;

import tools.jackson.databind.ObjectMapper;

class PaystackSubaccountServiceTest {

    @Test
    void createAndUpdateUseNeutralRequiredPercentageCharge() {
        CapturingClient client = new CapturingClient();
        PaystackSubaccountService service = new PaystackSubaccountService("live", "test-secret", client);
        Host host = new Host("host@example.com", "hash", "Payout Host", "+254711111111");
        HostPayoutSettingsUpsertRequest request = new HostPayoutSettingsUpsertRequest(PayoutMethod.BANK_ACCOUNT,
                "KEPSS-TEST", "0123456789", "Payout Host", null);

        String created = service.createOrUpdate(host, null, request);
        assertThat(created).isEqualTo("ACCT_TEST");
        assertThat(client.created.percentage_charge()).isEqualByComparingTo("0");

        HostPayoutSettings existing = new HostPayoutSettings(host, PayoutMethod.BANK_ACCOUNT, "KEPSS-TEST", "6789",
                "Payout Host", "ACCT_TEST", null, null, null);
        String updated = service.createOrUpdate(host, existing, request);
        assertThat(updated).isEqualTo("ACCT_TEST");
        assertThat(client.updated.percentage_charge()).isEqualByComparingTo("0");
    }

    private static final class CapturingClient extends PaystackApiClient {
        private SubaccountRequest created;
        private SubaccountRequest updated;

        private CapturingClient() {
            super("test-secret", new ObjectMapper());
        }

        @Override
        public String createSubaccount(SubaccountRequest request) {
            created = request;
            return "ACCT_TEST";
        }

        @Override
        public String updateSubaccount(String subaccountCode, SubaccountRequest request) {
            updated = request;
            return subaccountCode;
        }
    }
}
