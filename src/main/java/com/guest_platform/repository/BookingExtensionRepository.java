package com.guest_platform.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.guest_platform.entity.BookingExtension;
import com.guest_platform.entity.BookingExtensionStatus;

public interface BookingExtensionRepository extends JpaRepository<BookingExtension, UUID> {
    Optional<BookingExtension> findByIdAndBookingHostId(UUID id, UUID hostId);
    boolean existsByBookingPropertyIdAndStatusAndExpiresAtAfterAndOriginalCheckOutDateLessThanAndRequestedCheckOutDateGreaterThan(
            UUID propertyId, BookingExtensionStatus status, Instant now, LocalDate checkOutDate, LocalDate checkInDate);
}
