package com.guest_platform.dto;

import java.time.Instant;
import java.util.UUID;

import com.guest_platform.entity.Host;

public record HostResponse(UUID id, String email, String fullName, String phone, boolean active,
        Instant createdAt, Instant updatedAt) {
    public static HostResponse from(Host host) {
        return new HostResponse(host.getId(), host.getEmail(), host.getFullName(), host.getPhone(),
                host.isActive(), host.getCreatedAt(), host.getUpdatedAt());
    }
}
