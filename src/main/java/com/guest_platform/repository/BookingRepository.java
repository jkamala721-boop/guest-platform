package com.guest_platform.repository;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            select count(b) > 0 from Booking b where b.property.id = :propertyId
            and b.status in :statuses and b.checkInDate < :checkOut and b.checkOutDate > :checkIn
            and coalesce(b.id <> :excluded, true) = true
            and (b.offer is null or b.status not in (com.guest_platform.entity.BookingStatus.PENDING_CONFIRMATION,
                com.guest_platform.entity.BookingStatus.PENDING_PAYMENT)
                or (b.reservationConfirmedAt is null and b.checkoutHoldExpiresAt > :now)
                or (b.reservationConfirmedAt is not null and b.paymentDeadlineAt > :now))
            """)
    boolean hasBlockingReservation(@Param("propertyId") UUID propertyId,
            @Param("statuses") Collection<BookingStatus> statuses, @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut, @Param("excluded") UUID excluded, @Param("now") Instant now);

    @Query("""
            select b.id from Booking b where b.offer is not null
            and b.status in (com.guest_platform.entity.BookingStatus.PENDING_CONFIRMATION, com.guest_platform.entity.BookingStatus.PENDING_PAYMENT)
            and ((b.reservationConfirmedAt is null and b.checkoutHoldExpiresAt <= :now)
                or (b.reservationConfirmedAt is not null and b.paymentDeadlineAt <= :now))
            """)
    List<UUID> findExpiredOfferBookingIds(@Param("now") Instant now);

    boolean existsByGuestIdAndHostId(UUID guestId, UUID hostId);
    List<Booking> findAllByHostIdOrderByCreatedAtDesc(UUID hostId);
    Optional<Booking> findByIdAndHostId(UUID id, UUID hostId);

    @Query("""
            select booking.guest from Booking booking
            where booking.property.id = :propertyId and booking.id <> :currentBookingId
              and booking.status in (com.guest_platform.entity.BookingStatus.CONFIRMED, com.guest_platform.entity.BookingStatus.CHECKED_IN, com.guest_platform.entity.BookingStatus.COMPLETED)
              and booking.guest.active = true and booking.guest.identityType = :identityType
              and booking.guest.identityFingerprint = :fingerprint
            order by booking.updatedAt desc
            """)
    List<com.guest_platform.entity.Guest> findPriorConfirmedGuestsForProperty(@Param("propertyId") UUID propertyId,
            @Param("currentBookingId") UUID currentBookingId, @Param("identityType") String identityType,
            @Param("fingerprint") String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findForUpdateById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id and booking.host.id = :hostId")
    Optional<Booking> findForUpdateByIdAndHostId(@Param("id") UUID id, @Param("hostId") UUID hostId);

    boolean existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThan(UUID propertyId,
            Collection<BookingStatus> statuses, LocalDate checkOutDate, LocalDate checkInDate);

    boolean existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThanAndIdNot(UUID propertyId,
            Collection<BookingStatus> statuses, LocalDate checkOutDate, LocalDate checkInDate, UUID bookingId);

    @Query("select booking.host.id as hostId,booking.status as status,count(booking) as statusCount,max(booking.updatedAt) as lastActivityAt from Booking booking where booking.host.id in :hostIds group by booking.host.id,booking.status")
    List<HostBookingStatusSummary> summarizeByHostIds(@Param("hostIds") Collection<UUID> hostIds);
    interface HostBookingStatusSummary { UUID getHostId(); BookingStatus getStatus(); Long getStatusCount(); Instant getLastActivityAt(); }
    Page<Booking> findByHostId(UUID hostId, Pageable pageable);
}
