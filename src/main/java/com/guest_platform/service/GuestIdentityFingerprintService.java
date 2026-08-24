package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Creates deterministic, non-reversible identity match values. */
@Service
public class GuestIdentityFingerprintService {
    private final byte[] secret;
    public GuestIdentityFingerprintService(@Value("${app.security.guest-identity-fingerprint-secret}") String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("Guest identity fingerprint secret is required");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    public String normalize(String identityType, String rawValue) {
        if (identityType == null || rawValue == null || identityType.isBlank() || rawValue.isBlank()) throw new IllegalArgumentException("Identity type and number are required");
        return identityType.trim().toUpperCase(Locale.ROOT) + ':' + rawValue.replaceAll("[\\s\\-_/]", "").toUpperCase(Locale.ROOT);
    }
    public String fingerprint(String identityType, String rawValue) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return java.util.HexFormat.of().formatHex(mac.doFinal(normalize(identityType, rawValue).getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("Identity fingerprinting is unavailable", exception); }
    }
    public String masked(String rawValue) { String normalized = rawValue.replaceAll("[\\s\\-_/]", ""); int visible = Math.min(4, normalized.length()); return "*".repeat(Math.max(0, normalized.length() - visible)) + normalized.substring(normalized.length() - visible); }
}
