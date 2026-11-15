package com.guest_platform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.guest_platform.entity.*;
import com.guest_platform.dto.GuestLinkCreateResponse;
import com.guest_platform.exception.*;
import com.guest_platform.repository.*;

@Service
public class PropertyOfferService {
    private final PropertyOfferRepository offers;
    private final PropertyRepository properties;
    private final BookingRepository bookings;
    private final AvailabilityService availability;
    private final GuestLinkService links;
    private final HostOperationalAccessService access;
    private final EntityManager entityManager;
    private final long holdMinutes;
    public PropertyOfferService(PropertyOfferRepository offers, PropertyRepository properties,
            BookingRepository bookings, AvailabilityService availability, GuestLinkService links,
            HostOperationalAccessService access, EntityManager entityManager,
            @Value("${app.offers.checkout-hold-minutes:30}") long holdMinutes) {
        if (holdMinutes < 1 || holdMinutes > 30) throw new IllegalArgumentException("Offer hold must be 1–30 minutes");
        this.offers = offers; this.properties = properties; this.bookings = bookings; this.availability = availability;
        this.links = links; this.access = access; this.entityManager = entityManager; this.holdMinutes = holdMinutes;
    }
    public record CreateRequest(@NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal nightlyPrice) { }
    public record Dates(@NotNull LocalDate checkInDate, @NotNull LocalDate checkOutDate, UUID checkoutAttemptId) { }
    public record OfferView(UUID id, String propertyName, BigDecimal nightlyPrice, String currency,
            Instant expiresAt, Instant revokedAt) {
        static OfferView from(PropertyOffer o) { return new OfferView(o.getId(), o.getProperty().getName(),
                o.getNightlyPrice(), o.getCurrency(), o.getExpiresAt(), o.getRevokedAt()); }
    }
    public record CreatedOffer(OfferView offer, String token) { }
    public record Quote(LocalDate checkInDate, LocalDate checkOutDate, long nights, BigDecimal nightlyPrice,
            BigDecimal totalAmount, String currency, BigDecimal paystackServiceFee, BigDecimal paystackTotal,
            boolean available) { }
    public record Reservation(UUID bookingId, GuestLinkCreateResponse guestLink, Instant checkoutHoldExpiresAt,
            Instant paymentDeadlineAt) { }

    @Transactional
    public CreatedOffer create(UUID hostId, UUID propertyId, CreateRequest request) {
        access.requireAccess(hostId);
        Property p = owned(hostId, propertyId);
        if (!p.isActive() || !p.getHost().isActive()) throw new ConflictException("Property is not active");
        if (Currency.getInstance(p.getCurrency()).getDefaultFractionDigits() != 2)
            throw new IllegalArgumentException("Offers currently support currencies with two decimal places");
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PropertyOffer offer = offers.save(new PropertyOffer(p, hash(token), request.nightlyPrice(), Instant.now()));
        return new CreatedOffer(OfferView.from(offer), token);
    }
    @Transactional(readOnly=true)
    public List<OfferView> list(UUID hostId, UUID propertyId) {
        owned(hostId, propertyId);
        return offers.findByPropertyIdOrderByCreatedAtDesc(propertyId).stream().map(OfferView::from).toList();
    }
    @Transactional
    public void revoke(UUID hostId, UUID offerId) {
        PropertyOffer o = offers.findForUpdate(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer was not found"));
        if (!o.getProperty().getHost().getId().equals(hostId)) throw new ResourceNotFoundException("Offer was not found");
        o.revoke(Instant.now());
    }
    @Transactional(readOnly=true)
    public OfferView preview(String token) { return OfferView.from(usable(token)); }
    public record GalleryAccess(UUID propertyId, Instant expiresAt) { }
    @Transactional(readOnly=true)
    public GalleryAccess galleryAccess(String token) {
        PropertyOffer offer = usable(token);
        return new GalleryAccess(offer.getProperty().getId(), offer.getExpiresAt());
    }
    @Transactional(readOnly=true)
    public Quote quote(String token, Dates dates) { return calculate(usable(token), dates, Instant.now()); }
    @Transactional
    public Reservation reserve(String token, Dates dates) {
        if (dates.checkoutAttemptId() == null) throw new IllegalArgumentException("checkoutAttemptId is required");
        if (bookings.existsById(dates.checkoutAttemptId())) throw new ConflictException("Checkout attempt was already submitted; use its booking link or contact your host");
        PropertyOffer offer = usable(token);
        properties.findForUpdateById(offer.getProperty().getId()).orElseThrow();
        // Refresh after waiting: a concurrent revoke must be visible even in the persistence context.
        entityManager.refresh(offer, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(offer.getProperty());
        requireUsable(offer, Instant.now());
        if (bookings.existsById(dates.checkoutAttemptId())) throw new ConflictException("Checkout attempt was already submitted; use its booking link or contact your host");
        access.requireAccess(offer.getProperty().getHost().getId());
        Instant now = Instant.now();
        Quote quote = calculate(offer, dates, now);
        if (!quote.available()) throw new ConflictException("Property is unavailable for the requested dates");
        availability.requireAvailableFor(BookingStatus.PENDING_CONFIRMATION, offer.getProperty().getId(),
                dates.checkInDate(), dates.checkOutDate(), null);
        Booking booking = new Booking(offer.getProperty().getHost(), offer.getProperty());
        booking.update(offer.getProperty(), dates.checkInDate(), dates.checkOutDate(), quote.totalAmount(),
                offer.getCurrency(), BookingStatus.PENDING_CONFIRMATION, null);
        Instant checkIn = dates.checkInDate().atTime(offer.getProperty().getCheckInTime()).atZone(OfferBookingPolicy.ZONE).toInstant();
        Instant hold = now.plusSeconds(holdMinutes * 60);
        if (hold.isAfter(checkIn)) hold = checkIn;
        Instant paymentDeadline = checkIn.minusSeconds(86400).isAfter(now) ? checkIn.minusSeconds(43200) : hold;
        booking.attachOffer(offer, hold, paymentDeadline);
        booking.identifyOfferAttempt(dates.checkoutAttemptId());
        // Persist, never merge: a caller-chosen attempt ID must never update an existing booking.
        try {
            entityManager.persist(booking);
            entityManager.flush();
        } catch (jakarta.persistence.PersistenceException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState()))
                    throw new ConflictException("Checkout attempt was already submitted; use its booking link or contact your host");
            }
            throw exception;
        }
        return new Reservation(booking.getId(), links.createForNewBooking(booking), hold, paymentDeadline);
    }
    private Quote calculate(PropertyOffer offer, Dates dates, Instant now) {
        if (dates.checkInDate() == null || dates.checkOutDate() == null || !dates.checkOutDate().isAfter(dates.checkInDate()))
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        Instant checkIn = dates.checkInDate().atTime(offer.getProperty().getCheckInTime()).atZone(OfferBookingPolicy.ZONE).toInstant();
        if (!checkIn.isAfter(now)) throw new IllegalArgumentException("Check-in must be in the future");
        long nights = ChronoUnit.DAYS.between(dates.checkInDate(), dates.checkOutDate());
        BigDecimal total = offer.getNightlyPrice().multiply(BigDecimal.valueOf(nights)).setScale(2, RoundingMode.UNNECESSARY);
        if (total.precision() > 12) throw new IllegalArgumentException("Booking total exceeds the supported amount");
        BigDecimal fee = total.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        if (total.add(fee).precision() > 12) throw new IllegalArgumentException("Booking total with fees exceeds the supported amount");
        return new Quote(dates.checkInDate(), dates.checkOutDate(), nights, offer.getNightlyPrice(), total,
                offer.getCurrency(), fee, total.add(fee), availability.isAvailable(offer.getProperty().getId(),
                dates.checkInDate(), dates.checkOutDate(), null));
    }
    private Property owned(UUID hostId, UUID propertyId) {
        return properties.findByIdAndHostId(propertyId, hostId).orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
    }
    private PropertyOffer usable(String token) {
        PropertyOffer o = offers.findByTokenHash(hash(token)).orElseThrow(() -> new ResourceNotFoundException("Offer was not found"));
        requireUsable(o, Instant.now()); return o;
    }
    private void requireUsable(PropertyOffer offer, Instant now) {
        if (!offer.isUsableAt(now) || !offer.getProperty().isActive() || !offer.getProperty().getHost().isActive())
            throw new ResourceNotFoundException("Offer is expired or unavailable");
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
