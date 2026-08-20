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

    public void update(String fullName, String phone, String email, String idType, String idNumber,
            String nationality, String whatsappNumber, String notes) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.idType = idType;
        this.idNumber = idNumber;
        this.nationality = nationality;
        this.whatsappNumber = whatsappNumber;
        this.notes = notes;
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
}
