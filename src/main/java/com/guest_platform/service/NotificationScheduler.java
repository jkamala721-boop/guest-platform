package com.guest_platform.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final HostNotificationDeliveryService hostNotificationDeliveryService;

    public NotificationScheduler(NotificationService notificationService,
            HostNotificationDeliveryService hostNotificationDeliveryService) {
        this.notificationService = notificationService;
        this.hostNotificationDeliveryService = hostNotificationDeliveryService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.scheduler.fixed-delay-ms:60000}")
    public void reconcileAndDeliver() {
        notificationService.reconcileAndDeliver();
        hostNotificationDeliveryService.pendingIds().forEach(hostNotificationDeliveryService::deliver);
    }
}
