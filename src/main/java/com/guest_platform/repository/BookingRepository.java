package com.guest_platform.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findAllByHostIdOrderByCreatedAtDesc(UUID hostId);
    Optional<Booking> findByIdAndHostId(UUID id, UUID hostId);

    boolean existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThan(UUID propertyId,
            Collection<BookingStatus> statuses, LocalDate checkOutDate, LocalDate checkInDate);

    boolean existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThanAndIdNot(UUID propertyId,
            Collection<BookingStatus> statuses, LocalDate checkOutDate, LocalDate checkInDate, UUID bookingId);
}
