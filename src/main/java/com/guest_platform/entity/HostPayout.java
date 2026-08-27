package com.guest_platform.entity;

import java.math.BigDecimal;
import java.time.Duration;
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

    @Column(name = "transfer_code", unique = true, length = 100)
    private String transferCode;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private boolean retryable;

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
        this.providerStatus = "pending";
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

    /** A payout becomes available only after the configured settlement hold and a still-valid saved destination. */
    public boolean releaseIfEligible(HostPayoutSettings settings, Instant now, Duration settlementHold) {
        if (status != HostPayoutStatus.PENDING || payoutMethod != PayoutMethod.MPESA
                || payment.getStatus() != PaymentStatus.SUCCEEDED
                || payment.getBooking().getStatus() != BookingStatus.CONFIRMED
                || createdAt.plus(settlementHold).isAfter(now)
                || settings == null || settings.getStatus() != PayoutSettingsStatus.CONFIGURED
                || settings.getPayoutMethod() != PayoutMethod.MPESA
                || !recipientCode.equals(settings.getPaystackRecipientCode())) {
            return false;
        }
        status = HostPayoutStatus.AVAILABLE;
        providerStatus = "available";
        return true;
    }

    /** The durable state change happens before a remote transfer call to prevent duplicate submissions. */
    public boolean beginProcessing() {
        if (status != HostPayoutStatus.AVAILABLE || transferCode != null) {
            return false;
        }
        status = HostPayoutStatus.PROCESSING;
        providerStatus = "initiating";
        lastAttemptAt = Instant.now();
        attemptCount++;
        retryable = false;
        return true;
    }

    public boolean recordTransferAccepted(String acceptedReference, String acceptedTransferCode, String status) {
        if (this.status != HostPayoutStatus.PROCESSING || !providerReference.equals(acceptedReference)
                || acceptedTransferCode == null || acceptedTransferCode.isBlank()) {
            return false;
        }
        transferCode = acceptedTransferCode;
        providerStatus = safeStatus(status, "pending");
        return true;
    }

    public boolean markPaid(String acceptedReference, String acceptedTransferCode) {
        if (!providerReference.equals(acceptedReference) || status == HostPayoutStatus.PAID) {
            return false;
        }
        if (status != HostPayoutStatus.PROCESSING) {
            return false;
        }
        if (transferCode != null && acceptedTransferCode != null && !transferCode.equals(acceptedTransferCode)) {
            return false;
        }
        if (acceptedTransferCode != null && !acceptedTransferCode.isBlank()) {
            transferCode = acceptedTransferCode;
        }
        status = HostPayoutStatus.PAID;
        providerStatus = "success";
        failureReason = null;
        retryable = false;
        completedAt = Instant.now();
        return true;
    }

    public boolean markFailed(String providerStatus, String reason, boolean canRetry) {
        if (status != HostPayoutStatus.PROCESSING) {
            return false;
        }
        status = HostPayoutStatus.FAILED;
        this.providerStatus = safeStatus(providerStatus, "failed");
        failureReason = safeReason(reason);
        retryable = canRetry;
        return true;
    }

    public boolean confirmManual(String externalReference) {
        if (status == HostPayoutStatus.PAID) return externalReference != null && externalReference.equals(transferCode)
                && "manual_confirmed".equals(providerStatus);
        if ((status != HostPayoutStatus.AVAILABLE
                && (status != HostPayoutStatus.FAILED || retryable)) || transferCode != null) return false;
        transferCode = externalReference;
        status = HostPayoutStatus.PAID;
        providerStatus = "manual_confirmed";
        failureReason = null;
        retryable = false;
        completedAt = Instant.now();
        lastAttemptAt = completedAt;
        return true;
    }

    public boolean markManuallyFailed(String reason) {
        if (status == HostPayoutStatus.FAILED && !retryable && safeReason(reason).equals(failureReason)) return true;
        if (status != HostPayoutStatus.AVAILABLE) return false;
        status = HostPayoutStatus.FAILED;
        providerStatus = "manual_failure";
        failureReason = safeReason(reason);
        retryable = false;
        lastAttemptAt = Instant.now();
        return true;
    }

    public boolean restoreForVerifiedRetry() {
        if (status != HostPayoutStatus.FAILED || !retryable || transferCode != null) {
            return false;
        }
        status = HostPayoutStatus.AVAILABLE;
        providerStatus = "retryable";
        retryable = false;
        return true;
    }

    public boolean updateFromVerification(String verifiedReference, String verifiedTransferCode, String verifiedStatus) {
        if (!providerReference.equals(verifiedReference) || status != HostPayoutStatus.PROCESSING) {
            return false;
        }
        if (verifiedTransferCode != null && !verifiedTransferCode.isBlank()) {
            if (transferCode != null && !transferCode.equals(verifiedTransferCode)) {
                return false;
            }
            transferCode = verifiedTransferCode;
        }
        providerStatus = safeStatus(verifiedStatus, "pending");
        return true;
    }

    private String safeStatus(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() > 40 ? value.substring(0, 40) : value;
    }

    private String safeReason(String value) {
        if (value == null || value.isBlank()) {
            return "Paystack transfer failed";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
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
    public String getTransferCode() { return transferCode; }
    public String getProviderStatus() { return providerStatus; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public int getAttemptCount() { return attemptCount; }
    public boolean isRetryable() { return retryable; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
