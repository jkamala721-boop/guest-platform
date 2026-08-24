package com.guest_platform.dto;

import java.time.Instant;

import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.PayoutMethod;
import com.guest_platform.entity.PayoutSettingsStatus;

/** No API response contains the full bank-account number or Paystack subaccount code. */
public record HostPayoutSettingsResponse(boolean configured, PayoutMethod payoutMethod, String settlementBankCode,
        String maskedAccountNumber, String maskedMpesaPhone, String accountName, PayoutSettingsStatus status, Instant createdAt,
        Instant updatedAt) {

    public static HostPayoutSettingsResponse notConfigured() {
        return new HostPayoutSettingsResponse(false, null, null, null, null, null, null, null, null);
    }

    public static HostPayoutSettingsResponse from(HostPayoutSettings settings) {
        return new HostPayoutSettingsResponse(true, settings.getPayoutMethod(), settings.getSettlementBankCode(),
                mask(settings.getAccountNumberLast4()), mask(settings.getMpesaPhoneLast4()), settings.getAccountName(), settings.getStatus(),
                settings.getCreatedAt(), settings.getUpdatedAt());
    }

    private static String mask(String last4) {
        return last4 == null ? null : "****" + last4;
    }
}
