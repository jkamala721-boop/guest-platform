package com.guest_platform.dto;

import java.time.Instant;

import com.guest_platform.entity.GuestLinkState;

public record GuestLinkCreateResponse(String token, GuestLinkState state, Instant expiresAt) {
}
