package com.guest_platform.dto;

import com.guest_platform.entity.NotificationChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualNotificationRequest(@NotNull NotificationChannel channel,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 4000) String message) {
}
