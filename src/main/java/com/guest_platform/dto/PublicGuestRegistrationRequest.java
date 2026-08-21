package com.guest_platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public, token-authorized registration fields. Host-only notes are excluded. */
public record PublicGuestRegistrationRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 40) String idType,
        @Size(max = 100) String idNumber,
        @Size(max = 100) String nationality,
        @Size(max = 32) String whatsappNumber) {
}
