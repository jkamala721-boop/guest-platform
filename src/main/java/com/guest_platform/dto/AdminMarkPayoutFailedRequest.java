package com.guest_platform.dto;
import jakarta.validation.constraints.*;
public record AdminMarkPayoutFailedRequest(@NotBlank @Size(max=500) String reason) {}
