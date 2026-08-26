package com.guest_platform.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "host_verifications")
public class HostVerification {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "host_id", nullable = false, unique = true)
    private Host host;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private HostVerificationStatus status;
    @Column(name = "legal_name", nullable = false, length = 160) private String legalName;
    @Enumerated(EnumType.STRING) @Column(name = "verification_type", nullable = false, length = 40)
    private HostVerificationType verificationType;
    @Enumerated(EnumType.STRING) @Column(name = "id_type", nullable = false, length = 30)
    private HostIdentityType idType;
    @Column(name = "id_number_last4", nullable = false, length = 4) private String idNumberLast4;
    @Column(name = "id_fingerprint", nullable = false, length = 64) private String idFingerprint;
    @Column(nullable = false, length = 32) private String phone;
    @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "review_started_at") private Instant reviewStartedAt;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by_admin_id") private AdminUser reviewedByAdmin;
    @Column(name = "rejection_reason", length = 1000) private String rejectionReason;
    @Column(name = "suspension_reason", length = 1000) private String suspensionReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected HostVerification() {}
    public HostVerification(Host host, String legalName, HostVerificationType verificationType,
            HostIdentityType idType, String last4, String fingerprint, String phone, String countryCode) {
        this.host = host; this.status = HostVerificationStatus.SUBMITTED; this.legalName = legalName;
        this.verificationType = verificationType; this.idType = idType; this.idNumberLast4 = last4;
        this.idFingerprint = fingerprint; this.phone = phone; this.countryCode = countryCode;
        this.submittedAt = Instant.now();
    }
    @PrePersist void create() { if (id == null) id = UUID.randomUUID(); createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public void resubmit(String legalName, HostVerificationType type, HostIdentityType idType, String last4,
            String fingerprint, String phone, String countryCode) {
        require(HostVerificationStatus.REJECTED); this.legalName=legalName; this.verificationType=type;
        this.idType=idType; this.idNumberLast4=last4; this.idFingerprint=fingerprint; this.phone=phone;
        this.countryCode=countryCode; this.status=HostVerificationStatus.SUBMITTED; this.submittedAt=Instant.now();
        this.reviewStartedAt=null; this.reviewedAt=null; this.reviewedByAdmin=null; this.rejectionReason=null;
    }
    public void startReview(AdminUser admin) { require(HostVerificationStatus.SUBMITTED); status=HostVerificationStatus.UNDER_REVIEW; reviewStartedAt=Instant.now(); reviewedByAdmin=admin; }
    public void approve(AdminUser admin) { require(HostVerificationStatus.UNDER_REVIEW); status=HostVerificationStatus.VERIFIED; reviewedAt=Instant.now(); reviewedByAdmin=admin; rejectionReason=null; }
    public void reject(AdminUser admin, String reason) { require(HostVerificationStatus.UNDER_REVIEW); status=HostVerificationStatus.REJECTED; reviewedAt=Instant.now(); reviewedByAdmin=admin; rejectionReason=reason; }
    private void require(HostVerificationStatus expected) { if (status != expected) throw new IllegalStateException("Invalid host verification transition"); }
    public UUID getId(){return id;} public Host getHost(){return host;} public HostVerificationStatus getStatus(){return status;}
    public String getLegalName(){return legalName;} public HostVerificationType getVerificationType(){return verificationType;}
    public HostIdentityType getIdType(){return idType;} public String getIdNumberLast4(){return idNumberLast4;}
    public String getIdFingerprint(){return idFingerprint;} public String getPhone(){return phone;}
    public String getCountryCode(){return countryCode;} public Instant getSubmittedAt(){return submittedAt;}
    public Instant getReviewStartedAt(){return reviewStartedAt;} public Instant getReviewedAt(){return reviewedAt;}
    public String getRejectionReason(){return rejectionReason;} public String getSuspensionReason(){return suspensionReason;}
}
