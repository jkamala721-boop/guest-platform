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
@Table(name = "guest_links")
public class GuestLink {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GuestLinkState state;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GuestLink() {
    }

    public GuestLink(Booking booking, String tokenHash, Instant expiresAt) {
        this.booking = booking;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.state = GuestLinkState.REGISTRATION_OR_PAYMENT;
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

    public void revoke() {
        state = GuestLinkState.REVOKED;
        revokedAt = Instant.now();
    }

    public void expire() {
        state = GuestLinkState.EXPIRED;
    }

    public void activate() {
        if (state == GuestLinkState.REGISTRATION_OR_PAYMENT && revokedAt == null) {
            state = GuestLinkState.STAY_ACTIVE;
        }
    }

    public void extendExpiry(Instant newExpiresAt) {
        if (newExpiresAt.isAfter(expiresAt)) {
            expiresAt = newExpiresAt;
        }
    }

    public boolean isUsableAt(Instant now) {
        return (state == GuestLinkState.REGISTRATION_OR_PAYMENT || state == GuestLinkState.STAY_ACTIVE)
                && revokedAt == null && expiresAt.isAfter(now);
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public String getTokenHash() { return tokenHash; }
    public GuestLinkState getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
