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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_extension_id")
    private BookingExtension bookingExtension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "provider_reference", nullable = false, length = 200)
    private String providerReference;

    @Column(name = "provider_event_id", unique = true, length = 200)
    private String providerEventId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "booking_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal bookingAmount;

    @Column(name = "service_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceFee;

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
        this(host, booking, provider, providerReference, amount, BigDecimal.ZERO, amount, currency);
    }

    public Payment(Host host, Booking booking, PaymentProvider provider, String providerReference,
            BigDecimal bookingAmount, BigDecimal serviceFee, BigDecimal chargedAmount, String currency) {
        this.host = host;
        this.booking = booking;
        this.provider = provider;
        this.providerReference = providerReference;
        this.bookingAmount = bookingAmount;
        this.serviceFee = serviceFee;
        this.amount = chargedAmount;
        this.currency = currency;
        this.status = PaymentStatus.PROCESSING;
    }

    public Payment(Host host, Booking booking, BookingExtension bookingExtension, PaymentProvider provider,
            String providerReference, BigDecimal amount, String currency) {
        this(host, booking, provider, providerReference, amount, currency);
        this.bookingExtension = bookingExtension;
    }

    public Payment(Host host, Booking booking, BookingExtension bookingExtension, PaymentProvider provider,
            String providerReference, BigDecimal bookingAmount, BigDecimal serviceFee, BigDecimal chargedAmount,
            String currency) {
        this(host, booking, provider, providerReference, bookingAmount, serviceFee, chargedAmount, currency);
        this.bookingExtension = bookingExtension;
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
        if (status != PaymentStatus.PROCESSING && status != PaymentStatus.PENDING) {
            return false;
        }
        status = PaymentStatus.SUCCEEDED;
        providerEventId = eventId;
        failureReason = null;
        paidAt = Instant.now();
        return true;
    }

    public boolean markFailed(String eventId, String reason) {
        if (status != PaymentStatus.PROCESSING && status != PaymentStatus.PENDING) {
            return false;
        }
        status = PaymentStatus.FAILED;
        providerEventId = eventId;
        failureReason = reason;
        return true;
    }

    public boolean markCancelled(String eventId, String reason) {
        if (status != PaymentStatus.PROCESSING && status != PaymentStatus.PENDING) {
            return false;
        }
        status = PaymentStatus.CANCELLED;
        providerEventId = eventId;
        failureReason = reason;
        return true;
    }

    public void setProviderReference(String providerReference) {
        if (providerReference == null || providerReference.isBlank() || providerReference.length() > 200) {
            throw new IllegalArgumentException("providerReference is required");
        }
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment provider reference cannot be changed after processing");
        }
        this.providerReference = providerReference;
    }

    public UUID getId() { return id; }
    public Host getHost() { return host; }
    public Booking getBooking() { return booking; }
    public BookingExtension getBookingExtension() { return bookingExtension; }
    public PaymentProvider getProvider() { return provider; }
    public String getProviderReference() { return providerReference; }
    public String getProviderEventId() { return providerEventId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBookingAmount() { return bookingAmount; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
