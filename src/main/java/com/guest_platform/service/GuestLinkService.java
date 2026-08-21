package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.GuestLinkCreateResponse;
import com.guest_platform.dto.PublicGuestLinkResponse;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.GuestLinkRepository;

@Service
public class GuestLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final GuestLinkRepository guestLinkRepository;

    public GuestLinkService(BookingRepository bookingRepository, GuestLinkRepository guestLinkRepository) {
        this.bookingRepository = bookingRepository;
        this.guestLinkRepository = guestLinkRepository;
    }

    @Transactional
    public GuestLinkCreateResponse rotate(UUID hostId, UUID bookingId) {
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        guestLinkRepository.findAllByBookingIdAndStateNot(booking.getId(), GuestLinkState.REVOKED)
                .forEach(GuestLink::revoke);

        String token = newToken();
        Instant expiresAt = booking.getCheckOutDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        GuestLink guestLink = guestLinkRepository.save(new GuestLink(booking, hash(token), expiresAt));
        return new GuestLinkCreateResponse(token, guestLink.getState(), guestLink.getExpiresAt());
    }

    @Transactional
    public PublicGuestLinkResponse resolvePublic(String token) {
        GuestLink guestLink = guestLinkRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("Guest link was not found"));
        if (!guestLink.isUsableAt(Instant.now())) {
            if (guestLink.getRevokedAt() == null && guestLink.getState() != GuestLinkState.EXPIRED) {
                guestLink.expire();
            }
            throw new ResourceNotFoundException("Guest link was not found");
        }
        return PublicGuestLinkResponse.from(guestLink);
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
