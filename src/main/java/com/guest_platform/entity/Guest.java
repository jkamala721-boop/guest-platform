package com.guest_platform.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "guests")
public class Guest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verification_code_hash", length = 100)
    private String emailVerificationCodeHash;

    @Column(name = "email_verification_expires_at")
    private Instant emailVerificationExpiresAt;

    @Column(name = "email_verification_sent_at")
    private Instant emailVerificationSentAt;

    @Column(name = "email_verification_attempts", nullable = false)
    private int emailVerificationAttempts;

    @Column(name = "id_type", length = 40)
    private String idType;

    @Column(name = "id_number", length = 100)
    private String idNumber;

    @Column(length = 100)
    private String nationality;

    @Column(name = "whatsapp_number", length = 32)
    private String whatsappNumber;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean active = true;

    protected Guest() {
    }

    public Guest(Host host) {
        this.host = host;
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

    public boolean update(String fullName, String phone, String email, String idType, String idNumber,
            String nationality, String whatsappNumber, String notes) {
        boolean emailChanged = !Objects.equals(this.email, email);
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.idType = idType;
        this.idNumber = idNumber;
        this.nationality = nationality;
        this.whatsappNumber = whatsappNumber;
        this.notes = notes;
        if (emailChanged) {
            resetEmailVerification();
        }
        return emailChanged;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getIdType() { return idType; }
    public String getIdNumber() { return idNumber; }
    public String getNationality() { return nationality; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return active; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getEmailVerificationCodeHash() { return emailVerificationCodeHash; }
    public Instant getEmailVerificationExpiresAt() { return emailVerificationExpiresAt; }
    public Instant getEmailVerificationSentAt() { return emailVerificationSentAt; }
    public int getEmailVerificationAttempts() { return emailVerificationAttempts; }

    public void beginEmailVerification(String codeHash, Instant expiresAt, Instant sentAt) {
        emailVerified = false;
        emailVerificationCodeHash = codeHash;
        emailVerificationExpiresAt = expiresAt;
        emailVerificationSentAt = sentAt;
        emailVerificationAttempts = 0;
    }

    public boolean hasUsableEmailVerificationAt(Instant now, int maximumAttempts) {
        return emailVerificationCodeHash != null && emailVerificationExpiresAt != null
                && emailVerificationExpiresAt.isAfter(now) && emailVerificationAttempts < maximumAttempts;
    }

    public void recordEmailVerificationFailure() {
        emailVerificationAttempts++;
    }

    public void confirmEmailVerification() {
        emailVerified = true;
        clearEmailVerificationChallenge();
    }

    private void resetEmailVerification() {
        emailVerified = false;
        clearEmailVerificationChallenge();
    }

    private void clearEmailVerificationChallenge() {
        emailVerificationCodeHash = null;
        emailVerificationExpiresAt = null;
        emailVerificationSentAt = null;
        emailVerificationAttempts = 0;
    }

    public void archive() {
        active = false;
    }
}
