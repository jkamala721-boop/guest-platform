package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class PayoutDestinationFingerprintServiceTest {

    private static final String PHONE = "+254712345678";

    @Test
    void newFingerprintsUseTheDedicatedSecretWhileRecognisingLegacyValuesDuringMigration() {
        PayoutDestinationFingerprintService dedicated = new PayoutDestinationFingerprintService("dedicated-key", "legacy-paystack-key");
        PayoutDestinationFingerprintService legacy = new PayoutDestinationFingerprintService("legacy-paystack-key", "");

        String current = dedicated.fingerprint(PHONE);
        String legacyValue = legacy.fingerprint(PHONE);

        assertThat(current).isNotEqualTo(legacyValue);
        assertThat(dedicated.matchesExistingFingerprint(PHONE, current)).isTrue();
        assertThat(dedicated.matchesExistingFingerprint(PHONE, legacyValue)).isTrue();
    }

    @Test
    void dedicatedSecretIsMandatoryAndNeverFallsBackForNewFingerprints() {
        PayoutDestinationFingerprintService missing = new PayoutDestinationFingerprintService("", "legacy-paystack-key");

        assertThatIllegalStateException().isThrownBy(() -> missing.fingerprint(PHONE))
                .withMessage("Payout destination protection is not configured");
    }
}
