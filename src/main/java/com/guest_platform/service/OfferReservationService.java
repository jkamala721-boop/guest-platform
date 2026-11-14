package com.guest_platform.service;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.guest_platform.entity.*;
import com.guest_platform.repository.*;
import com.guest_platform.exception.*;

@Service
public class OfferReservationService {
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final GuestLinkRepository links;
    private final NotificationService notifications;
    private final EntityManager entityManager;
    public OfferReservationService(BookingRepository bookings, PropertyRepository properties,
            GuestLinkRepository links, NotificationService notifications, EntityManager entityManager) {
        this.bookings=bookings; this.properties=properties; this.links=links;
        this.notifications=notifications; this.entityManager=entityManager;
    }
    @Transactional
    public void confirm(GuestLink link) {
        Booking booking = bookings.findForUpdateById(link.getBooking().getId()).orElseThrow();
        entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
        properties.findForUpdateById(booking.getProperty().getId()).orElseThrow();
        Instant now = Instant.now();
        if (!booking.isOfferBooking() || !booking.isAwaitingOfferPayment() || booking.getGuest() == null
                || OfferBookingPolicy.expired(booking, now) || !link.isUsableAt(now))
            throw new ConflictException("Registration and a valid reservation are required");
        if (booking.getReservationConfirmedAt() != null) return;
        if (!OfferBookingPolicy.paymentOpensAt(booking).isAfter(now))
            throw new ConflictException("Payment is required to confirm this reservation");
        booking.confirmOfferReservation(now);
        notifications.reconcileBooking(booking.getId());
    }
    @Transactional
    public void expire(UUID id) {
        Booking booking = bookings.findForUpdateById(id).orElse(null);
        if (booking == null || !booking.isOfferBooking()) return;
        properties.findForUpdateById(booking.getProperty().getId()).orElseThrow();
        if (!OfferBookingPolicy.expired(booking, Instant.now())) return;
        booking.cancel();
        links.findAllByBookingId(id).forEach(GuestLink::revoke);
        notifications.cancelPendingForBooking(id);
    }
}
