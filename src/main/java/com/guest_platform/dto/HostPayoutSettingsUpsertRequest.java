package com.guest_platform.dto;

import com.guest_platform.entity.PayoutMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record HostPayoutSettingsUpsertRequest(
        @NotNull PayoutMethod payoutMethod,
        @Size(max = 80) String settlementBankCode,
        @Pattern(regexp = "^$|[0-9]{5,34}", message = "must contain 5 to 34 digits") String accountNumber,
        @Size(max = 160) String accountName,
        @Pattern(regexp = "^$|(?:\\+2547\\d{8}|2547\\d{8}|07\\d{8})", message = "must be a Kenyan M-Pesa number") String mpesaPhone) {

    @AssertTrue(message = "Bank payout settings require bank, account number, and account name; M-Pesa requires a valid Kenyan phone number")
    public boolean hasRequiredFieldsForMethod() {
        if (payoutMethod == PayoutMethod.BANK_ACCOUNT) {
            return notBlank(settlementBankCode) && notBlank(accountNumber) && notBlank(accountName);
        }
        return payoutMethod == PayoutMethod.MPESA && notBlank(mpesaPhone);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
