package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropertyAccessEncryptionServiceTest {
    private final PropertyAccessEncryptionService service = new PropertyAccessEncryptionService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void encryptsCodesWithoutPersistingPlaintext() {
        String ciphertext = service.encrypt("LOCK-1234");
        assertThat(ciphertext).doesNotContain("LOCK-1234");
        assertThat(service.decrypt(ciphertext)).isEqualTo("LOCK-1234");
    }
}
