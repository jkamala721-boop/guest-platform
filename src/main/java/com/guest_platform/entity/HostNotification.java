package com.guest_platform.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "host_notifications")
public class HostNotification {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    private HostPayout payout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private HostNotificationType type;

    @Column(name = "event_key", nullable = false, unique = true, length = 160)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HostNotificationStatus status = HostNotificationStatus.PENDING;

    @Column(name = "delivery_detail", length = 500)
    private String deliveryDetail;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostNotification() {}

    public HostNotification(Booking booking, HostPayout payout, HostNotificationType type, String eventKey) {
        this.host = booking.getHost();
        this.booking = booking;
        this.payout = payout;
        this.type = type;
        this.eventKey = eventKey;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void markSent(Instant now) {
        if (status == HostNotificationStatus.PENDING) {
            status = HostNotificationStatus.SENT;
            sentAt = now;
            deliveryDetail = "Host template email delivered";
        }
    }

    public void markFailed() {
        if (status == HostNotificationStatus.PENDING) {
            status = HostNotificationStatus.FAILED;
            deliveryDetail = "Delivery failed";
        }
    }

    public void markSkipped(String detail) {
        if (status == HostNotificationStatus.PENDING) {
            status = HostNotificationStatus.SKIPPED;
            deliveryDetail = detail;
        }
    }

    public UUID getId() { return id; }
    public Host getHost() { return host; }
    public Booking getBooking() { return booking; }
    public HostPayout getPayout() { return payout; }
    public HostNotificationType getType() { return type; }
    public String getEventKey() { return eventKey; }
    public HostNotificationStatus getStatus() { return status; }
    public String getDeliveryDetail() { return deliveryDetail; }
    public Instant getSentAt() { return sentAt; }
}

