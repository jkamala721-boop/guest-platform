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
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.GuestLinkRepository;
import com.guest_platform.repository.PaymentRepository;
import com.guest_platform.repository.ReceiptRepository;

@Service
public class GuestLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final GuestLinkRepository guestLinkRepository;
    private final PaymentRepository paymentRepository;
    private final ReceiptRepository receiptRepository;

    public GuestLinkService(BookingRepository bookingRepository, GuestLinkRepository guestLinkRepository,
            PaymentRepository paymentRepository, ReceiptRepository receiptRepository) {
        this.bookingRepository = bookingRepository;
        this.guestLinkRepository = guestLinkRepository;
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
    }

    @Transactional
    public GuestLinkCreateResponse rotate(UUID hostId, UUID bookingId) {
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
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
    public PublicGuestLinkResponse resolvePublic(String token) {
        GuestLink guestLink = resolveUsableGuestLink(token);
        if (guestLink.getState() == GuestLinkState.STAY_ACTIVE) {
            Receipt receipt = receiptRepository.findByBookingId(guestLink.getBooking().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Guest link was not found"));
            return PublicGuestStayResponse.from(guestLink, receipt);
        }
        PaymentStatus status = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(guestLink.getBooking().getId())
                .map(Payment::getStatus)
                .orElse(PaymentStatus.PENDING);
        return PublicGuestRegistrationOrPaymentResponse.from(guestLink, status);
    }

    @Transactional
    public void updateGuestRegistration(String token, PublicGuestRegistrationRequest request) {
        GuestLink guestLink = resolveUsableGuestLink(token);
        if (guestLink.getState() != GuestLinkState.REGISTRATION_OR_PAYMENT) {
            throw new ConflictException("Guest registration is no longer available");
        }
        Guest guest = guestLink.getBooking().getGuest();
        guest.update(request.fullName().trim(), request.phone().trim(), request.email().trim().toLowerCase(Locale.ROOT),
                normalizeOptional(request.idType()), normalizeOptional(request.idNumber()),
                normalizeOptional(request.nationality()), normalizeOptional(request.whatsappNumber()), guest.getNotes());
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

    @Transactional
    public GuestLink resolveUsableGuestLink(String token) {
        GuestLink guestLink = guestLinkRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("Guest link was not found"));
        if (!guestLink.isUsableAt(Instant.now())) {
            if (guestLink.getRevokedAt() == null && guestLink.getState() != GuestLinkState.EXPIRED) {
                guestLink.expire();
            }
            throw new ResourceNotFoundException("Guest link was not found");
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
