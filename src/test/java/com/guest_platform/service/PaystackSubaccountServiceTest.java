package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.guest_platform.dto.HostPayoutSettingsUpsertRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.PayoutMethod;
import com.guest_platform.service.payment.PaystackApiClient;

import tools.jackson.databind.ObjectMapper;

class PaystackSubaccountServiceTest {

    @Test
    void credentialCompatibilityFailsClosedAndAllowsOnlyExactKnownDomains() {
        HostPayoutSettings settings = mock(HostPayoutSettings.class);
        CapturingClient client = new CapturingClient();

        assertCompatibility(new PaystackSubaccountService("mock", "", client), settings, "mock", true);
        assertCompatibility(new PaystackSubaccountService("live", "sk_test_example", client), settings, "test", true);
        assertCompatibility(new PaystackSubaccountService("live", "sk_live_example", client), settings, "live", true);

        assertCompatibility(new PaystackSubaccountService("mock", "", client), settings, null, false);
        assertCompatibility(new PaystackSubaccountService("mock", "", client), settings, "   ", false);
        assertCompatibility(new PaystackSubaccountService("live", "sk_test_example", client), settings, "live", false);
        assertCompatibility(new PaystackSubaccountService("live", "sk_live_example", client), settings, "test", false);
        assertCompatibility(new PaystackSubaccountService("live", "sk_live_example", client), settings, "unexpected", false);
        assertCompatibility(new PaystackSubaccountService("live", "unrecognized-key", client), settings, "live", false);
    }

    @Test
    void createAndUpdateUseNeutralRequiredPercentageCharge() {
        CapturingClient client = new CapturingClient();
        PaystackSubaccountService service = new PaystackSubaccountService("live", "test-secret", client);
        Host host = new Host("host@example.com", "hash", "Payout Host", "+254711111111");
        HostPayoutSettingsUpsertRequest request = new HostPayoutSettingsUpsertRequest(PayoutMethod.BANK_ACCOUNT,
                "KEPSS-TEST", "0123456789", "Payout Host", null);

        PaystackApiClient.Subaccount created = service.createOrUpdate(host, null, request);
        assertThat(created.code()).isEqualTo("ACCT_TEST");
        assertThat(client.created.percentage_charge()).isEqualByComparingTo("0");

        HostPayoutSettings existing = new HostPayoutSettings(host, PayoutMethod.BANK_ACCOUNT, "KEPSS-TEST", "6789",
                "Payout Host", "ACCT_TEST", null, null, null);
        PaystackApiClient.Subaccount updated = service.createOrUpdate(host, existing, request);
        assertThat(updated.code()).isEqualTo("ACCT_TEST");
        assertThat(client.updated.percentage_charge()).isEqualByComparingTo("0");
    }

    private void assertCompatibility(PaystackSubaccountService service, HostPayoutSettings settings, String domain,
            boolean expected) {
        when(settings.getPaystackSubaccountDomain()).thenReturn(domain);
        assertThat(service.isCompatibleWithConfiguredCredentials(settings)).isEqualTo(expected);
    }

    private static final class CapturingClient extends PaystackApiClient {
        private SubaccountRequest created;
        private SubaccountRequest updated;

        private CapturingClient() {
            super("test-secret", new ObjectMapper());
        }

        @Override
        public Subaccount createSubaccount(SubaccountRequest request) {
            created = request;
            return new Subaccount("ACCT_TEST", 101L, "test", true, true);
        }

        @Override
        public Subaccount updateSubaccount(String subaccountCode, SubaccountRequest request) {
            updated = request;
            return new Subaccount(subaccountCode, 101L, "test", true, true);
        }
    }
}
