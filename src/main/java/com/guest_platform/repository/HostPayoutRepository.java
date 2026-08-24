package com.guest_platform.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.HostPayout;
import com.guest_platform.entity.HostPayoutStatus;

public interface HostPayoutRepository extends JpaRepository<HostPayout, UUID> {
    Optional<HostPayout> findByPaymentId(UUID paymentId);

    Optional<HostPayout> findByProviderReference(String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payout from HostPayout payout where payout.providerReference = :providerReference")
    Optional<HostPayout> findForUpdateByProviderReference(@Param("providerReference") String providerReference);

    List<HostPayout> findTop50ByStatusInOrderByCreatedAtAsc(Collection<HostPayoutStatus> statuses);

    List<HostPayout> findTop50ByStatusAndRetryableTrueOrderByLastAttemptAtAsc(HostPayoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payout from HostPayout payout where payout.id = :id")
    Optional<HostPayout> findForUpdateById(@Param("id") UUID id);
}
