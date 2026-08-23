package com.guest_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationConfirmRequest(
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
}
