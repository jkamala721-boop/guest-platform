package com.guest_platform.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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
@Table(name = "properties")
public class Property {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 40)
    private PropertyType propertyType;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "maps_url", nullable = false, length = 2048)
    private String mapsUrl;

    @Column(name = "max_guests", nullable = false)
    private int maxGuests;

    @Column(name = "default_nightly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultNightlyRate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime;

    @Column(name = "wifi_name", length = 100)
    private String wifiName;

    @Column(name = "wifi_password", length = 200)
    private String wifiPassword;

    @Column(name = "house_rules", length = 5000)
    private String houseRules;

    @Column(name = "check_in_instructions", length = 5000)
    private String checkInInstructions;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Property() {
    }

    public Property(Host host) {
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

    public void update(String name, PropertyType propertyType, String address, String mapsUrl,
            int maxGuests, BigDecimal defaultNightlyRate, String currency, LocalTime checkInTime,
            LocalTime checkOutTime, String wifiName, String wifiPassword, String houseRules,
            String checkInInstructions, String contactPhone, boolean active) {
        this.name = name;
        this.propertyType = propertyType;
        this.address = address;
        this.mapsUrl = mapsUrl;
        this.maxGuests = maxGuests;
        this.defaultNightlyRate = defaultNightlyRate;
        this.currency = currency;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.wifiName = wifiName;
        this.wifiPassword = wifiPassword;
        this.houseRules = houseRules;
        this.checkInInstructions = checkInInstructions;
        this.contactPhone = contactPhone;
        this.active = active;
    }

    public void deactivate() { this.active = false; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public PropertyType getPropertyType() { return propertyType; }
    public String getAddress() { return address; }
    public String getMapsUrl() { return mapsUrl; }
    public int getMaxGuests() { return maxGuests; }
    public BigDecimal getDefaultNightlyRate() { return defaultNightlyRate; }
    public String getCurrency() { return currency; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public String getWifiName() { return wifiName; }
    public String getWifiPassword() { return wifiPassword; }
    public String getHouseRules() { return houseRules; }
    public String getCheckInInstructions() { return checkInInstructions; }
    public String getContactPhone() { return contactPhone; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
