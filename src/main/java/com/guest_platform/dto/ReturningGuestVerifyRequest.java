package com.guest_platform.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record ReturningGuestVerifyRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) { }
