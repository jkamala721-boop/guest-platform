package com.guest_platform.service;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.guest_platform.repository.BookingRepository;

@Component
public class OfferReservationScheduler {
    private final BookingRepository bookings;
    private final OfferReservationService reservations;
    public OfferReservationScheduler(BookingRepository bookings, OfferReservationService reservations) {
        this.bookings=bookings; this.reservations=reservations;
    }
    @Scheduled(fixedDelayString="${app.offers.cleanup-delay-ms:60000}")
    public void expireReservations() {
        bookings.findExpiredOfferBookingIds(Instant.now()).forEach(reservations::expire);
    }
}
