package com.guest_platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;

public interface GuestLinkRepository extends JpaRepository<GuestLink, UUID> {
    Optional<GuestLink> findByTokenHash(String tokenHash);
    List<GuestLink> findAllByBookingIdAndStateNot(UUID bookingId, GuestLinkState state);
    List<GuestLink> findAllByBookingIdAndState(UUID bookingId, GuestLinkState state);
}
