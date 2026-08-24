package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Produces a keyed, non-reversible equality fingerprint; no raw M-Pesa number is persisted. */
@Service
public class PayoutDestinationFingerprintService {
    private final String secretKey;

    public PayoutDestinationFingerprintService(@Value("${app.payments.paystack.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    public String fingerprint(String normalizedPhone) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack integration is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect payout destination", exception);
        }
    }
}
