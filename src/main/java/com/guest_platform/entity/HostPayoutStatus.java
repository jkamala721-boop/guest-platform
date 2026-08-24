package com.guest_platform.entity;

/**
 * M-Pesa transfers are independent from guest payment completion. A PENDING payout
 * is created after an eligible Paystack payment and released only by a future
 * settlement-aware transfer worker.
 */
public enum HostPayoutStatus {
    PENDING,
    AVAILABLE,
    PROCESSING,
    PAID,
    FAILED
}
