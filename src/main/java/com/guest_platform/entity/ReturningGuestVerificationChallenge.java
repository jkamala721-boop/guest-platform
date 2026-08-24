package com.guest_platform.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "returning_guest_verification_challenges")
public class ReturningGuestVerificationChallenge {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "guest_link_id") private GuestLink guestLink;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "guest_id") private Guest guest;
    @Column(name = "code_hash", nullable = false, length = 100) private String codeHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "sent_at", nullable = false) private Instant sentAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected ReturningGuestVerificationChallenge() { }
    public ReturningGuestVerificationChallenge(GuestLink link, Guest guest, String hash, Instant expiresAt, Instant sentAt) { this.guestLink=link; this.guest=guest; this.codeHash=hash; this.expiresAt=expiresAt; this.sentAt=sentAt; }
    @PrePersist void create() { if (id==null) id=UUID.randomUUID(); Instant now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void update() { updatedAt=Instant.now(); }
    public boolean usableAt(Instant now, int maximumAttempts) { return verifiedAt == null && expiresAt.isAfter(now) && attempts < maximumAttempts; }
    public void fail() { attempts++; }
    public void verify() { verifiedAt=Instant.now(); }
    public UUID getId(){return id;} public GuestLink getGuestLink(){return guestLink;} public Guest getGuest(){return guest;}
    public String getCodeHash(){return codeHash;} public Instant getExpiresAt(){return expiresAt;} public Instant getSentAt(){return sentAt;}
}
