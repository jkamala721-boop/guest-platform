package com.guest_platform.dto;

import java.time.Instant;

import com.guest_platform.entity.Guest;

/** Public-safe email ownership verification state. */
public record EmailVerificationResponse(boolean emailVerified, Instant resendAvailableAt) {
    public static EmailVerificationResponse from(Guest guest, long resendCooldownSeconds) {
        Instant resendAvailableAt = guest.getEmailVerificationSentAt() == null ? null
                : guest.getEmailVerificationSentAt().plusSeconds(resendCooldownSeconds);
        return new EmailVerificationResponse(guest.isEmailVerified(), resendAvailableAt);
    }
}
