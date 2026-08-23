package com.guest_platform.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.guest_platform.entity.BookingExtension;
import com.guest_platform.entity.BookingExtensionStatus;

public interface BookingExtensionRepository extends JpaRepository<BookingExtension, UUID> {
    Optional<BookingExtension> findByIdAndBookingHostId(UUID id, UUID hostId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select extension from BookingExtension extension where extension.id = :id and extension.booking.host.id = :hostId")
    Optional<BookingExtension> findForUpdateByIdAndBookingHostId(@Param("id") UUID id, @Param("hostId") UUID hostId);
    boolean existsByBookingPropertyIdAndStatusAndExpiresAtAfterAndOriginalCheckOutDateLessThanAndRequestedCheckOutDateGreaterThan(
            UUID propertyId, BookingExtensionStatus status, Instant now, LocalDate checkOutDate, LocalDate checkInDate);
}
