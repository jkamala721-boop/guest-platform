package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        PaystackSubaccountService service = new PaystackSubaccountService("live", "sk_test_example", client);
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

    @Test
    void legacyUnknownDomainIsFetchedBeforeUpdateAndMismatchedDomainCreatesFresh() {
        Host host = new Host("legacy@example.com", "hash", "Legacy Host", "+254711111111");
        HostPayoutSettingsUpsertRequest request = new HostPayoutSettingsUpsertRequest(PayoutMethod.BANK_ACCOUNT,
                "KEPSS-TEST", "0123456789", "Legacy Host", null);

        HostPayoutSettings legacy = new HostPayoutSettings(host, PayoutMethod.BANK_ACCOUNT, "KEPSS-TEST", "6789",
                "Legacy Host", "ACCT_LEGACY", null, null, null);
        PaystackApiClient currentClient = mock(PaystackApiClient.class);
        when(currentClient.fetchSubaccount("ACCT_LEGACY"))
                .thenReturn(new PaystackApiClient.Subaccount("ACCT_LEGACY", 9L, "live", true, true));
        when(currentClient.updateSubaccount(org.mockito.ArgumentMatchers.eq("ACCT_LEGACY"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaystackApiClient.Subaccount("ACCT_LEGACY", 9L, "live", true, true));
        PaystackApiClient.Subaccount reconciled = new PaystackSubaccountService("live", "sk_live_example", currentClient)
                .createOrUpdate(host, legacy, request);
        assertThat(reconciled.code()).isEqualTo("ACCT_LEGACY");
        verify(currentClient).fetchSubaccount("ACCT_LEGACY");
        verify(currentClient).updateSubaccount(org.mockito.ArgumentMatchers.eq("ACCT_LEGACY"),
                org.mockito.ArgumentMatchers.any());

        HostPayoutSettings testDomain = new HostPayoutSettings(host, PayoutMethod.BANK_ACCOUNT, "KEPSS-TEST", "6789",
                "Legacy Host", "ACCT_TEST", null, null, null);
        testDomain.recordPaystackSubaccount("ACCT_TEST", 10L, "test", true, true);
        PaystackApiClient liveClient = mock(PaystackApiClient.class);
        when(liveClient.createSubaccount(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaystackApiClient.Subaccount("ACCT_LIVE", 11L, "live", true, true));
        PaystackApiClient.Subaccount replaced = new PaystackSubaccountService("live", "sk_live_example", liveClient)
                .createOrUpdate(host, testDomain, request);
        assertThat(replaced.code()).isEqualTo("ACCT_LIVE");
        verify(liveClient, never()).fetchSubaccount("ACCT_TEST");
        verify(liveClient, never()).updateSubaccount(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingProviderSubaccountIsReplacedInsteadOfBlindlyUpdated() {
        Host host = new Host("missing@example.com", "hash", "Missing Host", "+254711111111");
        HostPayoutSettings existing = new HostPayoutSettings(host, PayoutMethod.BANK_ACCOUNT, "KEPSS-TEST", "6789",
                "Missing Host", "ACCT_GONE", null, null, null);
        existing.recordPaystackSubaccount("ACCT_GONE", 12L, "live", true, true);
        HostPayoutSettingsUpsertRequest request = new HostPayoutSettingsUpsertRequest(PayoutMethod.BANK_ACCOUNT,
                "KEPSS-TEST", "0123456789", "Missing Host", null);
        PaystackApiClient client = mock(PaystackApiClient.class);
        when(client.fetchSubaccount("ACCT_GONE")).thenThrow(
                new PaystackApiClient.PaystackRequestRejectedException(404, "Subaccount not found"));
        when(client.createSubaccount(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaystackApiClient.Subaccount("ACCT_NEW", 13L, "live", true, true));

        assertThat(new PaystackSubaccountService("live", "sk_live_example", client)
                .createOrUpdate(host, existing, request).code()).isEqualTo("ACCT_NEW");
        verify(client, never()).updateSubaccount(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyRecipientOrMockCodeIsNeverSentToTheLiveSubaccountUpdateEndpoint() {
        Host host = new Host("wrong-kind@example.com", "hash", "Wrong Kind Host", "+254711111111");
        HostPayoutSettingsUpsertRequest request = new HostPayoutSettingsUpsertRequest(PayoutMethod.BANK_ACCOUNT,
                "KEPSS-TEST", "0123456789", "Wrong Kind Host", null);
        for (String staleCode : java.util.List.of("RCP_LEGACY", "ACCT_MOCK_LEGACY")) {
            HostPayoutSettings stale = new HostPayoutSettings(host, PayoutMethod.BANK_ACCOUNT, "KEPSS-TEST", "6789",
                    "Wrong Kind Host", staleCode, null, null, null);
            PaystackApiClient client = mock(PaystackApiClient.class);
            when(client.createSubaccount(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new PaystackApiClient.Subaccount("ACCT_LIVE_NEW", 14L, "live", true, true));

            assertThat(new PaystackSubaccountService("live", "sk_live_example", client)
                    .createOrUpdate(host, stale, request).code()).isEqualTo("ACCT_LIVE_NEW");
            verify(client, never()).fetchSubaccount(staleCode);
            verify(client, never()).updateSubaccount(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any());
        }
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

        @Override
        public Subaccount fetchSubaccount(String subaccountCode) {
            return new Subaccount(subaccountCode, 101L, "test", true, true);
        }
    }
}
