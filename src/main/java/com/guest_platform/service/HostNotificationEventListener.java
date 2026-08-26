package com.guest_platform.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HostNotificationEventListener {
    private final HostNotificationDeliveryService deliveryService;

    public HostNotificationEventListener(HostNotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(HostNotificationCreatedEvent event) {
        deliveryService.deliver(event.notificationId());
    }
}
