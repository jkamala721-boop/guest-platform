package com.guest_platform.service.notification;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;

public interface NotificationProvider {
    NotificationChannel channel();

    void deliver(Notification notification);
}
