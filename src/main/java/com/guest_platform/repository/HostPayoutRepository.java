package com.guest_platform.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.HostPayout;

public interface HostPayoutRepository extends JpaRepository<HostPayout, UUID> {
    Optional<HostPayout> findByPaymentId(UUID paymentId);
}
