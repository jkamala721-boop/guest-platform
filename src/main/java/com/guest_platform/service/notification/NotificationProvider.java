package com.guest_platform.service.notification;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;

public interface NotificationProvider {
    NotificationChannel channel();

    /** Returns a safe message when this delivery is not ready to be sent. */
    default String readinessError(Notification notification) {
        return null;
    }

    void deliver(Notification notification);
}
