package com.guest_platform.dto;

import java.time.Instant;

import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;

public record PublicGuestLinkResponse(GuestLinkState state, Instant expiresAt) {
    public static PublicGuestLinkResponse from(GuestLink guestLink) {
        return new PublicGuestLinkResponse(guestLink.getState(), guestLink.getExpiresAt());
    }
}
