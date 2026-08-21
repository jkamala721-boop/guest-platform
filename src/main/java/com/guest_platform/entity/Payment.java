package com.guest_platform.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "payments_provider_reference_key", columnNames = { "provider", "provider_reference" })
})
public class Payment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "provider_reference", nullable = false, length = 200)
    private String providerReference;

    @Column(name = "provider_event_id", unique = true, length = 200)
    private String providerEventId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(Host host, Booking booking, PaymentProvider provider, String providerReference,
            BigDecimal amount, String currency) {
        this.host = host;
        this.booking = booking;
        this.provider = provider;
        this.providerReference = providerReference;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PROCESSING;
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

    public boolean markSucceeded(String eventId) {
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.CANCELLED) {
            return false;
        }
        status = PaymentStatus.SUCCEEDED;
        providerEventId = eventId;
        failureReason = null;
        paidAt = Instant.now();
        return true;
    }

    public boolean markFailed(String eventId, String reason) {
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.CANCELLED) {
            return false;
        }
        status = PaymentStatus.FAILED;
        providerEventId = eventId;
        failureReason = reason;
        return true;
    }

    public UUID getId() { return id; }
    public Host getHost() { return host; }
    public Booking getBooking() { return booking; }
    public PaymentProvider getProvider() { return provider; }
    public String getProviderReference() { return providerReference; }
    public String getProviderEventId() { return providerEventId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
