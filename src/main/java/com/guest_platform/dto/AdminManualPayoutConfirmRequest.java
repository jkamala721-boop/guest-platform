package com.guest_platform.dto;
import jakarta.validation.constraints.*;
public record AdminManualPayoutConfirmRequest(@NotBlank @Size(max=100) String externalReference,@Size(max=1000) String note) {}
