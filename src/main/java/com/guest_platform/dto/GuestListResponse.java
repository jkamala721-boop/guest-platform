package com.guest_platform.dto;

import java.time.Instant;
import java.util.UUID;

import com.guest_platform.entity.Guest;

public record GuestListResponse(UUID id, String fullName, String phone, String email,
        Instant createdAt, Instant updatedAt) {
    public static GuestListResponse from(Guest guest) {
        return new GuestListResponse(guest.getId(), guest.getFullName(), guest.getPhone(), guest.getEmail(),
                guest.getCreatedAt(), guest.getUpdatedAt());
    }
}
