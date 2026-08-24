package com.guest_platform.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.guest_platform.dto.*;
import com.guest_platform.entity.*;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.repository.*;

/** Privacy-preserving prior-stay recognition: identity knowledge alone never returns PII. */
@Service
public class ReturningGuestRecognitionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GuestLinkService links; private final BookingRepository bookings; private final ReturningGuestVerificationChallengeRepository challenges;
    private final GuestIdentityFingerprintService identities; private final PasswordEncoder encoder; private final NotificationService notifications;
    private final long ttl; private final long cooldown; private final int attempts;
    public ReturningGuestRecognitionService(GuestLinkService links, BookingRepository bookings, ReturningGuestVerificationChallengeRepository challenges,
            GuestIdentityFingerprintService identities, PasswordEncoder encoder, NotificationService notifications,
            @Value("${app.returning-guest-verification.code-ttl-seconds:600}") long ttl,
            @Value("${app.returning-guest-verification.resend-cooldown-seconds:60}") long cooldown,
            @Value("${app.returning-guest-verification.maximum-attempts:5}") int attempts) {
        this.links=links; this.bookings=bookings; this.challenges=challenges; this.identities=identities; this.encoder=encoder; this.notifications=notifications; this.ttl=ttl; this.cooldown=cooldown; this.attempts=attempts;
    }
    @Transactional
    public ReturningGuestLookupResponse lookup(String token, ReturningGuestLookupRequest request) {
        GuestLink link=links.resolveUsableGuestLink(token); Booking current=link.getBooking();
        String type=request.identityType().trim().toUpperCase(Locale.ROOT); String fingerprint=identities.fingerprint(type, request.identityNumber());
        Guest prior=bookings.findPriorConfirmedGuestsForProperty(current.getProperty().getId(), current.getId(), type, fingerprint)
                .stream().findFirst().orElse(null);
        if (prior == null || !prior.isEmailVerified()) return new ReturningGuestLookupResponse(false, null, null);
        Instant now=Instant.now();
        var previous=challenges.findFirstByGuestLinkIdOrderByCreatedAtDesc(link.getId());
        if (previous.isPresent() && previous.get().getSentAt().plusSeconds(cooldown).isAfter(now)) throw new ConflictException("Please wait before requesting another verification code");
        String code=String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        challenges.save(new ReturningGuestVerificationChallenge(link, prior, encoder.encode(code), now.plusSeconds(ttl), now));
        notifications.sendReturningGuestVerification(current, prior, code, ttl);
        return new ReturningGuestLookupResponse(true, maskEmail(prior.getEmail()), now.plusSeconds(cooldown));
    }
    @Transactional(noRollbackFor=ConflictException.class)
    public ReturningGuestVerifyResponse verify(String token, String code) {
        GuestLink link=links.resolveUsableGuestLink(token);
        var challenge=challenges.findFirstByGuestLinkIdOrderByCreatedAtDesc(link.getId()).orElseThrow(() -> new ConflictException("Returning guest verification is required"));
        if (!challenge.usableAt(Instant.now(), attempts) || !encoder.matches(code, challenge.getCodeHash())) { challenge.fail(); throw new ConflictException("The verification code is invalid or has expired"); }
        challenge.verify(); Guest g=challenge.getGuest();
        return new ReturningGuestVerifyResponse(true, new ReturningGuestVerifyResponse.Prefill(g.getFullName(), g.getPhone(), g.getEmail(), g.getNationality(), g.getIdentityType(), g.getMaskedIdentity()));
    }
    private String maskEmail(String email) { int at=email.indexOf('@'); return at < 1 ? "Email address" : email.substring(0, 1) + "***" + email.substring(at); }
}
