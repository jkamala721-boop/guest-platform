package com.guest_platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.Receipt;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    List<Receipt> findAllByHostIdOrderByIssuedAtDesc(UUID hostId);

    Optional<Receipt> findByIdAndHostId(UUID id, UUID hostId);

    Optional<Receipt> findByBookingIdAndHostId(UUID bookingId, UUID hostId);

    Optional<Receipt> findByBookingId(UUID bookingId);

    Optional<Receipt> findByPaymentId(UUID paymentId);
}
