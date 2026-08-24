package com.guest_platform.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** AES-GCM encryption for a property's sensitive entry code. */
@Service
public class PropertyAccessEncryptionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;
    public PropertyAccessEncryptionService(@Value("${app.security.property-access-encryption-key}") String encodedKey) {
        try { byte[] decoded = Base64.getDecoder().decode(encodedKey == null ? "" : encodedKey); if (decoded.length != 32) throw new IllegalArgumentException(); key = new SecretKeySpec(decoded, "AES"); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("Property access encryption key must be a base64 32-byte key"); }
    }
    public String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try { byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce)); byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array()); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("Property access encryption failed", exception); }
    }
    public String decrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try { byte[] all = Base64.getDecoder().decode(value); byte[] nonce = java.util.Arrays.copyOfRange(all, 0, 12); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce)); return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8); }
        catch (GeneralSecurityException | IllegalArgumentException exception) { throw new IllegalStateException("Property access decryption failed", exception); }
    }
}
