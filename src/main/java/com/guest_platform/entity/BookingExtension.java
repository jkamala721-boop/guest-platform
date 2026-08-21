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
@Table(name = "booking_extensions")
public class BookingExtension {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
    @Column(name = "original_check_out_date", nullable = false) private LocalDate originalCheckOutDate;
    @Column(name = "requested_check_out_date", nullable = false) private LocalDate requestedCheckOutDate;
    @Column(name = "added_nights", nullable = false) private int addedNights;
    @Column(name = "original_booking_amount", nullable = false, precision = 12, scale = 2) private BigDecimal originalBookingAmount;
    @Column(name = "additional_amount", nullable = false, precision = 12, scale = 2) private BigDecimal additionalAmount;
    @Column(name = "resulting_total_amount", nullable = false, precision = 12, scale = 2) private BigDecimal resultingTotalAmount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private BookingExtensionStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected BookingExtension() { }
    public BookingExtension(Booking booking, LocalDate requestedCheckOutDate, BigDecimal additionalAmount, Instant expiresAt) {
        this.booking = booking; this.originalCheckOutDate = booking.getCheckOutDate(); this.requestedCheckOutDate = requestedCheckOutDate;
        this.addedNights = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(originalCheckOutDate, requestedCheckOutDate));
        this.originalBookingAmount = booking.getTotalAmount(); this.additionalAmount = additionalAmount;
        this.resultingTotalAmount = originalBookingAmount.add(additionalAmount); this.currency = booking.getCurrency();
        this.status = BookingExtensionStatus.PENDING_PAYMENT; this.expiresAt = expiresAt;
    }
    @PrePersist void onCreate() { if (id == null) id = UUID.randomUUID(); Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public boolean isPendingAt(Instant now) { return status == BookingExtensionStatus.PENDING_PAYMENT && expiresAt.isAfter(now); }
    public boolean confirm() { if (status != BookingExtensionStatus.PENDING_PAYMENT || !expiresAt.isAfter(Instant.now())) return false; status = BookingExtensionStatus.CONFIRMED; return true; }
    public void fail() { if (status == BookingExtensionStatus.PENDING_PAYMENT) status = BookingExtensionStatus.FAILED; }
    public void expireIfNecessary(Instant now) { if (status == BookingExtensionStatus.PENDING_PAYMENT && !expiresAt.isAfter(now)) status = BookingExtensionStatus.EXPIRED; }
    public UUID getId(){return id;} public Booking getBooking(){return booking;} public LocalDate getOriginalCheckOutDate(){return originalCheckOutDate;}
    public LocalDate getRequestedCheckOutDate(){return requestedCheckOutDate;} public int getAddedNights(){return addedNights;}
    public BigDecimal getOriginalBookingAmount(){return originalBookingAmount;} public BigDecimal getAdditionalAmount(){return additionalAmount;}
    public BigDecimal getResultingTotalAmount(){return resultingTotalAmount;} public String getCurrency(){return currency;}
    public BookingExtensionStatus getStatus(){return status;} public Instant getExpiresAt(){return expiresAt;}
}
