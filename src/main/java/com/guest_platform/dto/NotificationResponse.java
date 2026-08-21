package com.guest_platform.dto;

import java.time.Instant;
import java.util.UUID;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;
import com.guest_platform.entity.NotificationStatus;
import com.guest_platform.entity.NotificationType;

/** Host-scoped operational status; guest contacts and guest-link tokens are excluded. */
public record NotificationResponse(UUID id, UUID bookingId, NotificationType type, NotificationChannel channel,
        NotificationStatus status, Instant scheduledAt, Instant sentAt, Boolean extensionAvailable,
        String deliveryDetail, Instant createdAt, Instant updatedAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getBooking().getId(), notification.getType(),
                notification.getChannel(), notification.getStatus(), notification.getScheduledAt(), notification.getSentAt(),
                notification.getExtensionAvailable(), notification.getDeliveryDetail(), notification.getCreatedAt(),
                notification.getUpdatedAt());
    }
}
