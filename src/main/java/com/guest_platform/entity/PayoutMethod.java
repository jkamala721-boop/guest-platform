package com.guest_platform.entity;

/** A bank destination settles through a Paystack subaccount; M-Pesa uses a transfer recipient. */
public enum PayoutMethod {
    BANK_ACCOUNT,
    MPESA
}
