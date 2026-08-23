package com.guest_platform.dto;

import java.time.Instant;
import java.util.UUID;

import com.guest_platform.entity.Guest;

public record GuestResponse(UUID id, String fullName, String phone, String email, String idType,
        String idNumber, String nationality, String whatsappNumber, String notes,
        boolean emailVerified, Instant createdAt, Instant updatedAt) {
    public static GuestResponse from(Guest guest) {
        return new GuestResponse(guest.getId(), guest.getFullName(), guest.getPhone(), guest.getEmail(),
                guest.getIdType(), guest.getIdNumber(), guest.getNationality(), guest.getWhatsappNumber(),
                guest.getNotes(), guest.isEmailVerified(), guest.getCreatedAt(), guest.getUpdatedAt());
    }
}
