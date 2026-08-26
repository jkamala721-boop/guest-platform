package com.guest_platform.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_users")
public class AdminUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 320) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AdminRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AdminStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "last_login_at") private Instant lastLoginAt;

    protected AdminUser() {}

    public AdminUser(String email, String passwordHash, String displayName, AdminRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.status = AdminStatus.ACTIVE;
    }

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public void recordLogin(Instant now) { lastLoginAt = now; }
    public void disable() { status = AdminStatus.DISABLED; }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public AdminRole getRole() { return role; }
    public AdminStatus getStatus() { return status; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
