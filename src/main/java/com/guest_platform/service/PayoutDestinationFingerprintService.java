package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Produces a keyed, non-reversible equality fingerprint; no raw M-Pesa number is persisted. */
@Service
public class PayoutDestinationFingerprintService {
    private final String fingerprintSecret;
    private final String legacyPaystackSecret;

    public PayoutDestinationFingerprintService(
            @Value("${app.security.payout-fingerprint-secret:}") String fingerprintSecret,
            @Value("${app.payments.paystack.secret-key:}") String legacyPaystackSecret) {
        this.fingerprintSecret = fingerprintSecret;
        this.legacyPaystackSecret = legacyPaystackSecret;
    }

    public String fingerprint(String normalizedPhone) {
        requireFingerprintSecret();
        return hmac(fingerprintSecret, normalizedPhone);
    }

    /** Migration-free compatibility only for fingerprints created before key separation. */
    public boolean matchesExistingFingerprint(String normalizedPhone, String storedFingerprint) {
        if (storedFingerprint == null || storedFingerprint.isBlank()) return false;
        if (MessageDigest.isEqual(fingerprint(normalizedPhone).getBytes(StandardCharsets.UTF_8),
                storedFingerprint.getBytes(StandardCharsets.UTF_8))) return true;
        return legacyPaystackSecret != null && !legacyPaystackSecret.isBlank()
                && MessageDigest.isEqual(hmac(legacyPaystackSecret, normalizedPhone).getBytes(StandardCharsets.UTF_8),
                        storedFingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private void requireFingerprintSecret() {
        if (fingerprintSecret == null || fingerprintSecret.isBlank()) {
            throw new IllegalStateException("Payout destination protection is not configured");
        }
    }

    private String hmac(String secret, String normalizedPhone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect payout destination", exception);
        }
    }
}
