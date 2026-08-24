package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuestIdentityFingerprintServiceTest {
    private final GuestIdentityFingerprintService service = new GuestIdentityFingerprintService("test-identity-secret");

    @Test
    void normalizesSeparatorsAndKeepsIdentityTypesSeparate() {
        assertThat(service.fingerprint("NATIONAL_ID", "AB- 12/34"))
                .isEqualTo(service.fingerprint("national_id", "ab1234"))
                .isNotEqualTo(service.fingerprint("PASSPORT", "AB1234"));
        assertThat(service.masked("AB-12 34")).isEqualTo("**1234");
    }
}
