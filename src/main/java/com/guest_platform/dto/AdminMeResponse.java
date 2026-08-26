package com.guest_platform.dto;

import java.util.UUID;
import com.guest_platform.entity.AdminRole;
import com.guest_platform.entity.AdminUser;

public record AdminMeResponse(UUID id, String email, String displayName, AdminRole role) {
    public static AdminMeResponse from(AdminUser admin) {
        return new AdminMeResponse(admin.getId(), admin.getEmail(), admin.getDisplayName(), admin.getRole());
    }
}

