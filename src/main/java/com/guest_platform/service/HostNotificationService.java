package com.guest_platform.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.HostNotification;
import com.guest_platform.entity.HostNotificationType;
import com.guest_platform.entity.HostPayout;
import com.guest_platform.repository.HostNotificationRepository;

@Service
public class HostNotificationService {
    private final HostNotificationRepository repository;
    private final ApplicationEventPublisher events;

    public HostNotificationService(HostNotificationRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public void paymentConfirmed(Booking booking, UUID paymentId) {
        record(booking, null, HostNotificationType.PAYMENT_CONFIRMED,
                "PAYMENT_CONFIRMED:" + paymentId);
    }

    @Transactional
    public void bookingCancelled(Booking booking) {
        record(booking, null, HostNotificationType.BOOKING_CANCELLED,
                "BOOKING_CANCELLED:" + booking.getId());
    }

    @Transactional
    public void payoutIssue(HostPayout payout) {
        record(payout.getPayment().getBooking(), payout, HostNotificationType.PAYOUT_ISSUE,
                "PAYOUT_ISSUE:" + payout.getId());
    }

    private void record(Booking booking, HostPayout payout, HostNotificationType type, String eventKey) {
        if (repository.existsByEventKey(eventKey)) return;
        // Callers hold the authoritative booking/payout row lock. The unique key is a
        // second durable guard against duplicates, not an exception-driven control path.
        HostNotification saved = repository.save(new HostNotification(booking, payout, type, eventKey));
        events.publishEvent(new HostNotificationCreatedEvent(saved.getId()));
    }
}
