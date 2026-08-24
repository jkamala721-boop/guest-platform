package com.guest_platform.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Host-scoped Paystack destination details. Bank accounts settle through a
 * Paystack subaccount. M-Pesa destinations use a Paystack transfer recipient.
 * Raw destination numbers are never retained by Hostvero.
 */
@Entity
@Table(name = "host_payout_settings")
public class HostPayoutSettings {

    @Id
    @Column(name = "host_id")
    private java.util.UUID hostId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false, length = 30)
    private PayoutMethod payoutMethod;

    @Column(name = "settlement_bank_code", length = 80)
    private String settlementBankCode;

    @Column(name = "account_number_last4", length = 4)
    private String accountNumberLast4;

    @Column(name = "account_name", length = 160)
    private String accountName;

    @Column(name = "paystack_subaccount_code", unique = true, length = 100)
    private String paystackSubaccountCode;

    @Column(name = "paystack_recipient_code", unique = true, length = 100)
    private String paystackRecipientCode;

    @Column(name = "mpesa_phone_last4", length = 4)
    private String mpesaPhoneLast4;

    @Column(name = "mpesa_phone_fingerprint", length = 64)
    private String mpesaPhoneFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayoutSettingsStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HostPayoutSettings() {
    }

    public HostPayoutSettings(Host host, PayoutMethod payoutMethod, String settlementBankCode,
            String accountNumberLast4, String accountName, String paystackSubaccountCode,
            String paystackRecipientCode, String mpesaPhoneLast4, String mpesaPhoneFingerprint) {
        this.host = host;
        this.payoutMethod = payoutMethod;
        this.settlementBankCode = settlementBankCode;
        this.accountNumberLast4 = accountNumberLast4;
        this.accountName = accountName;
        this.paystackSubaccountCode = paystackSubaccountCode;
        this.paystackRecipientCode = paystackRecipientCode;
        this.mpesaPhoneLast4 = mpesaPhoneLast4;
        this.mpesaPhoneFingerprint = mpesaPhoneFingerprint;
        this.status = PayoutSettingsStatus.CONFIGURED;
    }

    public void update(PayoutMethod payoutMethod, String settlementBankCode, String accountNumberLast4,
            String accountName, String paystackSubaccountCode, String paystackRecipientCode, String mpesaPhoneLast4,
            String mpesaPhoneFingerprint) {
        this.payoutMethod = payoutMethod;
        this.settlementBankCode = settlementBankCode;
        this.accountNumberLast4 = accountNumberLast4;
        this.accountName = accountName;
        this.paystackSubaccountCode = paystackSubaccountCode;
        this.paystackRecipientCode = paystackRecipientCode;
        this.mpesaPhoneLast4 = mpesaPhoneLast4;
        this.mpesaPhoneFingerprint = mpesaPhoneFingerprint;
        this.status = PayoutSettingsStatus.CONFIGURED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public java.util.UUID getHostId() { return hostId; }
    public PayoutMethod getPayoutMethod() { return payoutMethod; }
    public String getSettlementBankCode() { return settlementBankCode; }
    public String getAccountNumberLast4() { return accountNumberLast4; }
    public String getAccountName() { return accountName; }
    public String getPaystackSubaccountCode() { return paystackSubaccountCode; }
    public String getPaystackRecipientCode() { return paystackRecipientCode; }
    public String getMpesaPhoneLast4() { return mpesaPhoneLast4; }
    public String getMpesaPhoneFingerprint() { return mpesaPhoneFingerprint; }
    public PayoutSettingsStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
