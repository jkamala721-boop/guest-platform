package com.guest_platform.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** One independent, provider-transfer payout obligation for a verified payment. */
@Entity
@Table(name = "host_payouts")
public class HostPayout {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false, length = 30)
    private PayoutMethod payoutMethod;

    @Column(name = "recipient_code", nullable = false, length = 100)
    private String recipientCode;

    @Column(name = "provider_reference", nullable = false, unique = true, length = 64)
    private String providerReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HostPayoutStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostPayout() {
    }

    public HostPayout(Payment payment, String recipientCode) {
        this.payment = payment;
        this.host = payment.getHost();
        this.payoutMethod = PayoutMethod.MPESA;
        this.recipientCode = recipientCode;
        this.providerReference = "payout_" + UUID.randomUUID();
        this.amount = payment.getBookingAmount();
        this.currency = payment.getCurrency();
        this.status = HostPayoutStatus.PENDING;
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

    public UUID getId() { return id; }
    public Payment getPayment() { return payment; }
    public Host getHost() { return host; }
    public PayoutMethod getPayoutMethod() { return payoutMethod; }
    public String getRecipientCode() { return recipientCode; }
    public String getProviderReference() { return providerReference; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public HostPayoutStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}
