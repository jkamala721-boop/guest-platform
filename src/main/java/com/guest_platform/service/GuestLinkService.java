package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.GuestLinkCreateResponse;
import com.guest_platform.dto.PublicGuestLinkResponse;
import com.guest_platform.dto.PublicGuestRegistrationOrPaymentResponse;
import com.guest_platform.dto.PublicGuestRegistrationRequest;
import com.guest_platform.dto.PublicGuestStayResponse;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.entity.Guest;
import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;
import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentStatus;
import com.guest_platform.entity.Receipt;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.GuestLinkExpiredException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.GuestLinkRepository;
import com.guest_platform.repository.PaymentRepository;
import com.guest_platform.repository.ReceiptRepository;
import com.guest_platform.repository.GuestRepository;

@Service
public class GuestLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final GuestLinkRepository guestLinkRepository;
    private final PaymentRepository paymentRepository;
    private final ReceiptRepository receiptRepository;
    private final GuestRepository guestRepository;
    private final NotificationService notificationService;
    private final PropertyAccessEncryptionService propertyAccessEncryptionService;
    private final GuestIdentityFingerprintService identityFingerprintService;
    private final long emailVerificationResendCooldownSeconds;
    private final HostOperationalAccessService operationalAccess;

    public GuestLinkService(BookingRepository bookingRepository, GuestLinkRepository guestLinkRepository,
            PaymentRepository paymentRepository, ReceiptRepository receiptRepository,
            GuestRepository guestRepository, NotificationService notificationService,
            PropertyAccessEncryptionService propertyAccessEncryptionService, GuestIdentityFingerprintService identityFingerprintService,
            @Value("${app.guest-email-verification.resend-cooldown-seconds:60}") long emailVerificationResendCooldownSeconds,
            HostOperationalAccessService operationalAccess) {
        this.bookingRepository = bookingRepository;
        this.guestLinkRepository = guestLinkRepository;
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
        this.guestRepository = guestRepository;
        this.notificationService = notificationService;
        this.propertyAccessEncryptionService = propertyAccessEncryptionService;
        this.identityFingerprintService = identityFingerprintService;
        this.emailVerificationResendCooldownSeconds = emailVerificationResendCooldownSeconds;
        this.operationalAccess = operationalAccess;
    }

    @Transactional
    public GuestLinkCreateResponse rotate(UUID hostId, UUID bookingId) {
        operationalAccess.requireAccess(hostId);
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("A guest link cannot be created for a cancelled booking");
        }
        Instant now = Instant.now();
        boolean hasActiveStayLink = guestLinkRepository
                .findAllByBookingIdAndState(booking.getId(), GuestLinkState.STAY_ACTIVE).stream()
                .anyMatch(link -> link.isUsableAt(now));
        if (hasActiveStayLink) {
            throw new ConflictException("An active guest stay link cannot be rotated after payment");
        }
        guestLinkRepository.findAllByBookingIdAndStateNot(booking.getId(), GuestLinkState.REVOKED)
                .forEach(GuestLink::revoke);

        String token = newToken();
        // Property time zones are not modelled yet. Checkout is therefore interpreted as UTC
        // consistently for all links until a per-property timezone is introduced.
        Instant expiresAt = booking.getCheckOutDate().atTime(booking.getProperty().getCheckOutTime())
                .toInstant(ZoneOffset.UTC);
        GuestLink guestLink = guestLinkRepository.save(new GuestLink(booking, hash(token), expiresAt));
        if (hasVerifiedPaymentForConfirmedBooking(booking)) {
            guestLink.activate();
        }
        return new GuestLinkCreateResponse(token, guestLink.getState(), guestLink.getExpiresAt());
    }

    @Transactional
    public GuestLinkCreateResponse createForNewBooking(Booking booking) {
        String token = newToken();
        Instant expiresAt = booking.getCheckOutDate().atTime(booking.getProperty().getCheckOutTime())
                .toInstant(ZoneOffset.UTC);
        GuestLink guestLink = guestLinkRepository.save(new GuestLink(booking, hash(token), expiresAt));
        return new GuestLinkCreateResponse(token, guestLink.getState(), guestLink.getExpiresAt());
    }

    @Transactional
    public void synchronizeExpiryForBooking(Booking booking) {
        Instant expiresAt = booking.getCheckOutDate().atTime(booking.getProperty().getCheckOutTime())
                .toInstant(ZoneOffset.UTC);
        guestLinkRepository.findAllByBookingId(booking.getId()).forEach(link -> link.extendExpiry(expiresAt));
    }

    @Transactional
    public PublicGuestLinkResponse resolvePublic(String token) {
        GuestLink guestLink = resolveUsableGuestLink(token);
        if (guestLink.getState() == GuestLinkState.STAY_ACTIVE) {
            Receipt receipt = receiptRepository.findFirstByBookingIdOrderByIssuedAtDesc(guestLink.getBooking().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Guest link was not found"));
            return PublicGuestStayResponse.from(guestLink, receipt,
                    propertyAccessEncryptionService.decrypt(guestLink.getBooking().getProperty().getAccessCodeCiphertext()));
        }
        PaymentStatus status = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(guestLink.getBooking().getId())
                .map(Payment::getStatus)
                .orElse(PaymentStatus.PENDING);
        return PublicGuestRegistrationOrPaymentResponse.from(guestLink, status, emailVerificationResendCooldownSeconds);
    }

    @Transactional
    public void updateGuestRegistration(String token, PublicGuestRegistrationRequest request) {
        GuestLink guestLink = resolveUsableGuestLink(token);

        if (guestLink.getState() != GuestLinkState.REGISTRATION_OR_PAYMENT) {
            throw new ConflictException("Guest registration is no longer available");
        }

        Booking booking = guestLink.getBooking();
        Guest guest = booking.getGuest();

        if (guest == null) {
            guest = new Guest(booking.getHost());

            guest.update(
                    request.fullName().trim(),
                    request.phone().trim(),
                    request.email().trim().toLowerCase(Locale.ROOT),
                    normalizeOptional(request.idType()),
                    normalizeOptional(request.idNumber()),
                    normalizeOptional(request.nationality()),
                    normalizeOptional(request.whatsappNumber()),
                    null
            );

            guest = guestRepository.save(guest);

            booking.assignGuest(guest);

        } else {
            guest.update(
                    request.fullName().trim(),
                    request.phone().trim(),
                    request.email().trim().toLowerCase(Locale.ROOT),
                    normalizeOptional(request.idType()),
                    normalizeOptional(request.idNumber()),
                    normalizeOptional(request.nationality()),
                    normalizeOptional(request.whatsappNumber()),
                    guest.getNotes()
            );
        }

        if (normalizeOptional(request.idType()) != null && normalizeOptional(request.idNumber()) != null) {
            String type = normalizeOptional(request.idType()).toUpperCase(Locale.ROOT);
            guest.setProtectedIdentity(type, identityFingerprintService.fingerprint(type, request.idNumber()),
                    identityFingerprintService.masked(request.idNumber()));
        } else {
            guest.clearProtectedIdentity();
        }

        booking.prepareForPayment();
        notificationService.reconcileBooking(booking.getId());
    }

    @Transactional
    public void activateForConfirmedBooking(Booking booking) {
        if (!hasVerifiedPaymentForConfirmedBooking(booking)) {
            return;
        }
        Instant now = Instant.now();
        guestLinkRepository.findAllByBookingIdAndState(booking.getId(), GuestLinkState.REGISTRATION_OR_PAYMENT)
                .stream()
                .filter(link -> link.isUsableAt(now))
                .forEach(GuestLink::activate);
    }

    /** Keeps cancelled booking links auditable while invalidating usable public links. */
    @Transactional
    public void revokeUsableLinksForCancelledBooking(Booking booking) {
        guestLinkRepository.findAllByBookingId(booking.getId()).stream()
                .filter(link -> link.getState() == GuestLinkState.REGISTRATION_OR_PAYMENT
                        || link.getState() == GuestLinkState.STAY_ACTIVE)
                .forEach(GuestLink::revoke);
    }

    @Transactional
    public GuestLink resolveUsableGuestLink(String token) {
        GuestLink guestLink = guestLinkRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("Guest link was not found"));
        if (guestLink.getBooking().getStatus() == BookingStatus.CANCELLED || guestLink.getRevokedAt() != null) {
            throw new ResourceNotFoundException("Guest link was not found");
        }
        if (!guestLink.isUsableAt(Instant.now())) {
            if (guestLink.getState() != GuestLinkState.EXPIRED) {
                guestLink.expire();
            }
            throw new GuestLinkExpiredException();
        }
        return guestLink;
    }

    private boolean hasVerifiedPaymentForConfirmedBooking(Booking booking) {
        return booking.getStatus() == BookingStatus.CONFIRMED
                && paymentRepository.existsByBookingIdAndStatus(booking.getId(), PaymentStatus.SUCCEEDED);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
