package com.guest_platform.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_sessions")
public class AdminSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false) private AdminUser adminUser;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;

    protected AdminSession() {}
    public AdminSession(AdminUser adminUser, String tokenHash, Instant expiresAt) {
        this.adminUser = adminUser;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        lastUsedAt = createdAt;
    }
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && adminUser.getStatus() == AdminStatus.ACTIVE;
    }
    public void touch(Instant now) { if (isUsableAt(now)) lastUsedAt = now; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public UUID getId() { return id; }
    public AdminUser getAdminUser() { return adminUser; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}

