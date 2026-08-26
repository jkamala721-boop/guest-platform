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
@Table(name = "admin_audit_log")
public class AdminAuditLog {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "admin_user_id") private AdminUser adminUser;
    @Column(nullable = false, length = 80) private String action;
    @Column(name = "entity_type", length = 80) private String entityType;
    @Column(name = "entity_id", length = 100) private String entityId;
    @Column(name = "previous_state", length = 4000) private String previousState;
    @Column(name = "new_state", length = 4000) private String newState;
    @Column(length = 1000) private String reason;
    @Column(name = "metadata_json", length = 4000) private String metadataJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AdminAuditLog() {}
    public AdminAuditLog(AdminUser adminUser, String action, String entityType, String entityId) {
        this.adminUser = adminUser;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
    }
    @PrePersist void onCreate() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public AdminUser getAdminUser() { return adminUser; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getPreviousState() { return previousState; }
    public String getNewState() { return newState; }
    public String getReason() { return reason; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
