package com.guest_platform.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72)
        @Pattern(regexp = ".*[a-z].*", message = "password must include a lowercase letter")
        @Pattern(regexp = ".*[A-Z].*", message = "password must include an uppercase letter")
        @Pattern(regexp = ".*\\d.*", message = "password must include a digit")
        @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "password must include a symbol") String password,
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 32) String phone,
        @NotBlank String passwordConfirmation) {

    @AssertTrue(message = "password confirmation must match")
    public boolean isPasswordConfirmationValid() {
        return password != null && password.equals(passwordConfirmation);
    }
}
