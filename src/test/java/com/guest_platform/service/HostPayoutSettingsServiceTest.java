package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.PayoutMethod;
import com.guest_platform.entity.PayoutSettingsStatus;
import com.guest_platform.exception.LifecycleConflictException;
import com.guest_platform.repository.HostPayoutSettingsRepository;
import com.guest_platform.repository.HostRepository;

class HostPayoutSettingsServiceTest {

    private static final UUID HOST_ID = UUID.randomUUID();

    @Test
    void automaticBankSettlementFailsSafelyForMissingUnknownMismatchedOrInactiveMetadata() {
        assertNotReady(null);
        assertNotReady(bankSettings(null, true, true));
        assertNotReady(bankSettings("   ", true, true));
        assertNotReady(bankSettings("live", true, true));
        assertNotReady(bankSettings("mock", false, true));
    }

    @Test
    void activeUnverifiedSubaccountRemainsUsableAndMpesaManualFallbackIgnoresSubaccountDomain() {
        HostPayoutSettings bank = bankSettings("mock", true, false);
        PaystackPayoutDestination automatic = service(bank).requireConfiguredPaystackDestination(HOST_ID);
        assertThat(automatic.method()).isEqualTo(PayoutMethod.BANK_ACCOUNT);
        assertThat(automatic.subaccountCode()).isEqualTo("ACCT_OWNER");

        HostPayoutSettings mpesa = mock(HostPayoutSettings.class);
        when(mpesa.getStatus()).thenReturn(PayoutSettingsStatus.CONFIGURED);
        when(mpesa.getPayoutMethod()).thenReturn(PayoutMethod.MPESA);
        when(mpesa.getPaystackRecipientCode()).thenReturn("RCP_OWNER");
        when(mpesa.getPaystackSubaccountDomain()).thenReturn(null);
        PaystackPayoutDestination manual = service(mpesa).requireConfiguredPaystackDestination(HOST_ID);
        assertThat(manual.method()).isEqualTo(PayoutMethod.MPESA);
        assertThat(manual.providerReference()).isEqualTo("RCP_OWNER");
        assertThat(manual.subaccountCode()).isNull();
    }

    private void assertNotReady(HostPayoutSettings settings) {
        assertThatThrownBy(() -> service(settings).requireConfiguredPaystackDestination(HOST_ID))
                .isInstanceOfSatisfying(LifecycleConflictException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("HOST_PAYOUT_ACCOUNT_NOT_READY");
                    assertThat(exception.getMessage()).isEqualTo(
                            "Host payout account is not ready for automatic settlement.");
                });
    }

    private HostPayoutSettings bankSettings(String domain, boolean active, boolean verified) {
        HostPayoutSettings settings = mock(HostPayoutSettings.class);
        when(settings.getStatus()).thenReturn(PayoutSettingsStatus.CONFIGURED);
        when(settings.getPayoutMethod()).thenReturn(PayoutMethod.BANK_ACCOUNT);
        when(settings.getPaystackSubaccountCode()).thenReturn("ACCT_OWNER");
        when(settings.getPaystackSubaccountDomain()).thenReturn(domain);
        when(settings.getPaystackSubaccountActive()).thenReturn(active);
        when(settings.getPaystackSubaccountVerified()).thenReturn(verified);
        return settings;
    }

    private HostPayoutSettingsService service(HostPayoutSettings settings) {
        HostPayoutSettingsRepository repository = mock(HostPayoutSettingsRepository.class);
        when(repository.findByHostId(HOST_ID)).thenReturn(Optional.ofNullable(settings));
        PaystackSubaccountService subaccounts = new PaystackSubaccountService("mock", "", mock(
                com.guest_platform.service.payment.PaystackApiClient.class));
        return new HostPayoutSettingsService(mock(HostRepository.class), repository, subaccounts,
                mock(PaystackTransferRecipientService.class), mock(PaystackBankDirectoryService.class),
                mock(PayoutDestinationFingerprintService.class));
    }
}
