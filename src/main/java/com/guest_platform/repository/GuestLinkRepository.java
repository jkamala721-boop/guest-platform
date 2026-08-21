package com.guest_platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;

public interface GuestLinkRepository extends JpaRepository<GuestLink, UUID> {
    @Query("select guestLink from GuestLink guestLink join fetch guestLink.booking booking join fetch booking.property where guestLink.tokenHash = :tokenHash")
    Optional<GuestLink> findByTokenHash(@Param("tokenHash") String tokenHash);
    List<GuestLink> findAllByBookingIdAndStateNot(UUID bookingId, GuestLinkState state);
    List<GuestLink> findAllByBookingIdAndState(UUID bookingId, GuestLinkState state);
    List<GuestLink> findAllByBookingId(UUID bookingId);
}
