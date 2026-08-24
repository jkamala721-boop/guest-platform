package com.guest_platform.dto;

import com.guest_platform.entity.PayoutMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HostPayoutSettingsUpsertRequest(
        @NotNull PayoutMethod payoutMethod,
        @NotBlank @Size(max = 80) String settlementBankCode,
        @NotBlank @Pattern(regexp = "[0-9]{5,34}", message = "must contain 5 to 34 digits") String accountNumber,
        @NotBlank @Size(max = 160) String accountName) {
}
