package com.guest_platform.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "property_offers")
public class PropertyOffer {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false) private Property property;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "nightly_price", nullable = false, precision = 12, scale = 2) private BigDecimal nightlyPrice;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    protected PropertyOffer() { }
    public PropertyOffer(Property property, String tokenHash, BigDecimal price, Instant now) {
        this.id = UUID.randomUUID(); this.property = property; this.tokenHash = tokenHash;
        this.nightlyPrice = price; this.currency = property.getCurrency();
        this.createdAt = now; this.expiresAt = now.plusSeconds(7 * 86400);
    }
    public boolean isUsableAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public UUID getId() { return id; }
    public Property getProperty() { return property; }
    public BigDecimal getNightlyPrice() { return nightlyPrice; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
