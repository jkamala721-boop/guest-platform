package com.guest_platform.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "bookings")
public class Booking {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "guest_id", nullable = true)
    private Guest guest;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookingStatus status;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
    }

    public Booking(Host host, Property property) {
    this.host = host;
    this.property = property;
    }

    public void assignGuest(Guest guest) {
        if (guest == null) {
        throw new IllegalArgumentException("guest cannot be null");
        }

        this.guest = guest;
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

    public void update(
            Property property,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            BigDecimal totalAmount,
            String currency,
            BookingStatus status,
            String notes) {

        this.property = property;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = status;
        this.notes = notes;
    }

    public void cancel() {
        status = BookingStatus.CANCELLED;
    }

    public boolean confirmAfterVerifiedPayment() {
        if (status != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        status = BookingStatus.CONFIRMED;
        return true;
    }

    /**
     * A standard host-created booking awaits guest registration before it can
     * enter the payment workflow.  The transition is intentionally performed
     * by the secure guest-link flow, not by a browser-only payment response.
     */
    public void prepareForPayment() {
        if (status == BookingStatus.PENDING_CONFIRMATION) {
            status = BookingStatus.PENDING_PAYMENT;
        }
    }

    public void extendTo(LocalDate newCheckOutDate, BigDecimal resultingTotalAmount) {
        if (!newCheckOutDate.isAfter(checkOutDate)) {
            throw new IllegalArgumentException("newCheckOutDate must be after the current checkOutDate");
        }
        checkOutDate = newCheckOutDate;
        totalAmount = resultingTotalAmount;
    }

    public UUID getId() { return id; }
    public Host getHost() { return host; }
    public Property getProperty() { return property; }
    public Guest getGuest() { return guest; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public BookingStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
