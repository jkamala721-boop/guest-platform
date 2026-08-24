package com.guest_platform.service;

import com.guest_platform.entity.PayoutMethod;

/** Provider reference snapshot used by a Paystack payment. */
public record PaystackPayoutDestination(PayoutMethod method, String providerReference) {
    public String subaccountCode() {
        return method == PayoutMethod.BANK_ACCOUNT ? providerReference : null;
    }
}
