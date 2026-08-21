package com.guest_platform.entity;

public enum BookingStatus {
    DRAFT,
    PENDING_CONFIRMATION,
    PENDING_PAYMENT,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED;

    public boolean blocksAvailability() {
        return this == PENDING_CONFIRMATION || this == PENDING_PAYMENT
                || this == CONFIRMED || this == CHECKED_IN;
    }
}
