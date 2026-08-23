package com.guest_platform.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.EmailVerificationResponse;
import com.guest_platform.entity.Guest;
import com.guest_platform.entity.GuestLink;
import com.guest_platform.exception.ConflictException;

/** Issues and verifies mailbox-ownership codes through a valid guest link. */
@Service
public class GuestEmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GuestLinkService guestLinkService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final long codeTtlSeconds;
    private final long resendCooldownSeconds;
    private final int maximumAttempts;

    public GuestEmailVerificationService(GuestLinkService guestLinkService, NotificationService notificationService,
            PasswordEncoder passwordEncoder,
            @Value("${app.guest-email-verification.code-ttl-seconds:600}") long codeTtlSeconds,
            @Value("${app.guest-email-verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.guest-email-verification.maximum-attempts:5}") int maximumAttempts) {
        if (codeTtlSeconds < 1 || resendCooldownSeconds < 1 || maximumAttempts < 1) {
            throw new IllegalArgumentException("Guest email verification configuration is invalid");
        }
        this.guestLinkService = guestLinkService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.codeTtlSeconds = codeTtlSeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maximumAttempts = maximumAttempts;
    }

    @Transactional
    public EmailVerificationResponse requestCode(String token) {
        GuestLink guestLink = guestLinkService.resolveUsableGuestLink(token);
        Guest guest = requireGuest(guestLink);
        if (guest.isEmailVerified()) {
            return status(guest);
        }

        Instant now = Instant.now();
        if (guest.getEmailVerificationSentAt() != null
                && guest.getEmailVerificationSentAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            throw new ConflictException("Please wait before requesting another verification code");
        }

        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        guest.beginEmailVerification(passwordEncoder.encode(code), now.plusSeconds(codeTtlSeconds), now);
        var delivery = notificationService.sendEmailVerification(guestLink.getBooking(), code, codeTtlSeconds);
        if (delivery.status() != com.guest_platform.entity.NotificationStatus.SENT) {
            throw new ConflictException("Verification email could not be delivered");
        }
        return status(guest);
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public EmailVerificationResponse confirmCode(String token, String code) {
        Guest guest = guestFor(token);
        Instant now = Instant.now();
        if (!guest.hasUsableEmailVerificationAt(now, maximumAttempts)) {
            throw new ConflictException("The verification code is invalid or has expired");
        }
        if (!passwordEncoder.matches(code, guest.getEmailVerificationCodeHash())) {
            guest.recordEmailVerificationFailure();
            throw new ConflictException("The verification code is invalid or has expired");
        }
        guest.confirmEmailVerification();
        return status(guest);
    }

    private Guest guestFor(String token) {
        return requireGuest(guestLinkService.resolveUsableGuestLink(token));
    }

    private Guest requireGuest(GuestLink guestLink) {
        Guest guest = guestLink.getBooking().getGuest();
        if (guest == null) {
            throw new ConflictException("Guest registration is required before email verification");
        }
        return guest;
    }

    private EmailVerificationResponse status(Guest guest) {
        return EmailVerificationResponse.from(guest, resendCooldownSeconds);
    }
}
