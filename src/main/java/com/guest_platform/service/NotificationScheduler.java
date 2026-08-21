package com.guest_platform.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final NotificationService notificationService;

    public NotificationScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.scheduler.fixed-delay-ms:60000}")
    public void reconcileAndDeliver() {
        notificationService.reconcileAndDeliver();
    }
}
