package com.guest_platform.entity;

/**
 * Controls when a valid guest link may reveal the property's stay details.
 * Payment and booking confirmation remain independent of this policy.
 */
public enum GuestAccessPolicy {
    AFTER_PAYMENT,
    BEFORE_PAYMENT
}
