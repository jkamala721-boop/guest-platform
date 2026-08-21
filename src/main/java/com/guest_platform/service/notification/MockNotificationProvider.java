package com.guest_platform.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;

@Component
public class MockNotificationProvider implements NotificationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockNotificationProvider.class);

    public MockNotificationProvider(@Value("${app.notifications.mode:mock}") String mode) {
        if (!"mock".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Only mock notifications are configured in Phase 6");
        }
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.MOCK;
    }

    @Override
    public void deliver(Notification notification) {
        LOGGER.info("Mock notification delivered: type={}, bookingId={}, channel={}", notification.getType(),
                notification.getBooking().getId(), notification.getChannel());
    }
}
