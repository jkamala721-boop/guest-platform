package com.guest_platform.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByBookingIdAndHostIdOrderByCreatedAtDesc(UUID bookingId, UUID hostId);

    Optional<Payment> findByIdAndHostId(UUID id, UUID hostId);

    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    boolean existsByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    boolean existsByBookingIdAndStatusIn(UUID bookingId, Collection<PaymentStatus> statuses);

    boolean existsByBookingExtensionIdAndStatusIn(UUID extensionId, Collection<PaymentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    Optional<Payment> findForUpdateById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.provider = :provider "
            + "and payment.providerReference = :providerReference")
    Optional<Payment> findForUpdateByProviderAndProviderReference(@Param("provider") PaymentProvider provider,
            @Param("providerReference") String providerReference);
}
