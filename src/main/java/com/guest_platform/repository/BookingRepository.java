package com.guest_platform.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    boolean existsByGuestIdAndHostId(UUID guestId, UUID hostId);
    List<Booking> findAllByHostIdOrderByCreatedAtDesc(UUID hostId);
    Optional<Booking> findByIdAndHostId(UUID id, UUID hostId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findForUpdateById(@Param("id") UUID id);

    boolean existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThan(UUID propertyId,
            Collection<BookingStatus> statuses, LocalDate checkOutDate, LocalDate checkInDate);

    boolean existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThanAndIdNot(UUID propertyId,
            Collection<BookingStatus> statuses, LocalDate checkOutDate, LocalDate checkInDate, UUID bookingId);
}
