package com.guest_platform.service;

import java.time.*;
import com.guest_platform.entity.Booking;

/** Nairobi-first policy. Existing bookings retain their original UTC interpretation. */
public final class OfferBookingPolicy {
    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");
    private OfferBookingPolicy() { }
    public static Instant checkInAt(Booking booking) {
        return booking.getCheckInDate().atTime(booking.getProperty().getCheckInTime())
                .atZone(booking.isOfferBooking() ? ZONE : ZoneOffset.UTC).toInstant();
    }
    public static Instant checkOutAt(Booking booking) {
        return booking.getCheckOutDate().atTime(booking.getProperty().getCheckOutTime())
                .atZone(booking.isOfferBooking() ? ZONE : ZoneOffset.UTC).toInstant();
    }
    public static Instant paymentOpensAt(Booking booking) { return checkInAt(booking).minusSeconds(86400); }
    public static boolean expired(Booking booking, Instant now) {
        if (!booking.isOfferBooking() || !booking.isAwaitingOfferPayment()) return false;
        Instant deadline = booking.getReservationConfirmedAt() == null
                ? booking.getCheckoutHoldExpiresAt() : booking.getPaymentDeadlineAt();
        return !deadline.isAfter(now);
    }
    public static boolean canPay(Booking booking, Instant now) {
        return !booking.isOfferBooking() || (booking.isAwaitingOfferPayment() && booking.getGuest() != null && !expired(booking, now)
                && !paymentOpensAt(booking).isAfter(now) && checkInAt(booking).isAfter(now));
    }
}
