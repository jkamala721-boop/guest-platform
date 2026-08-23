package com.guest_platform.entity;

import java.time.Instant;
import java.util.List;
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
import jakarta.persistence.Transient;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "extension_available")
    private Boolean extensionAvailable;

    @Column(name = "delivery_detail", length = 500)
    private String deliveryDetail;

    @Column(length = 200)
    private String subject;

    @Column(length = 4000)
    private String message;

    /**
     * One-time provider parameters, such as a raw secure-link URL. They are
     * intentionally never persisted.
     */
    @Transient
    private List<String> deliveryParameters = List.of();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Notification() {
    }

    public Notification(Booking booking, NotificationType type, NotificationChannel channel, Instant scheduledAt) {
        this.host = booking.getHost();
        this.booking = booking;
        this.guest = booking.getGuest();
        this.type = type;
        this.channel = channel;
        this.scheduledAt = scheduledAt;
        this.status = NotificationStatus.PENDING;
    }

    public Notification(Booking booking, NotificationChannel channel, String subject, String message, Instant scheduledAt) {
        this(booking, NotificationType.MANUAL_MESSAGE, channel, scheduledAt);
        this.subject = subject;
        this.message = message;
    }

    public Notification(Booking booking, NotificationType type, NotificationChannel channel, String subject,
            String message, Instant scheduledAt) {
        this(booking, type, channel, scheduledAt);
        this.subject = subject;
        this.message = message;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void reschedule(Instant scheduledAt) {
        if (status == NotificationStatus.PENDING) {
            this.scheduledAt = scheduledAt;
        }
    }

    public void refreshRecipient(Guest guest) {
        if (status == NotificationStatus.PENDING) {
            this.guest = guest;
        }
    }

    public void cancel() {
        if (status == NotificationStatus.PENDING) {
            status = NotificationStatus.CANCELLED;
        }
    }

    public void markSent(Instant sentAt, Boolean extensionAvailable, String deliveryDetail) {
        if (status == NotificationStatus.PENDING) {
            status = NotificationStatus.SENT;
            this.sentAt = sentAt;
            this.extensionAvailable = extensionAvailable;
            this.deliveryDetail = deliveryDetail;
        }
    }

    public void markFailed(String deliveryDetail) {
        if (status == NotificationStatus.PENDING) {
            status = NotificationStatus.FAILED;
            this.deliveryDetail = deliveryDetail;
        }
    }

    public boolean isDueAt(Instant now) {
        return status == NotificationStatus.PENDING && !scheduledAt.isAfter(now);
    }

    public UUID getId() { return id; }
    public Host getHost() { return host; }
    public Booking getBooking() { return booking; }
    public Guest getGuest() { return guest; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public NotificationStatus getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getSentAt() { return sentAt; }
    public Boolean getExtensionAvailable() { return extensionAvailable; }
    public String getDeliveryDetail() { return deliveryDetail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public List<String> getDeliveryParameters() { return deliveryParameters; }
    public void setDeliveryParameters(List<String> deliveryParameters) {
        this.deliveryParameters = deliveryParameters == null ? List.of() : List.copyOf(deliveryParameters);
    }
}
