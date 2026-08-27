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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.entity.PayoutMethod;

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

    @EntityGraph(attributePaths = {"host", "payment", "payment.booking"})
    @Query("select payout from HostPayout payout join payout.host host join payout.payment payment "
            + "where (:hostId is null or host.id=:hostId) and (:status is null or payout.status=:status) "
            + "and (:provider is null or payment.provider=:provider) and (:method is null or payout.payoutMethod=:method) "
            + "and (:q is null or lower(host.email) like :q or lower(host.fullName) like :q "
            + "or lower(payout.providerReference) like :q or lower(coalesce(payout.transferCode,'')) like :q)")
    Page<HostPayout> searchAdmin(@Param("hostId") UUID hostId, @Param("status") HostPayoutStatus status,
            @Param("provider") PaymentProvider provider, @Param("method") PayoutMethod method,
            @Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"host", "payment", "payment.booking"})
    @Query("select payout from HostPayout payout where payout.id=:id")
    Optional<HostPayout> findAdminDetailById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"payment", "payment.booking"})
    Page<HostPayout> findByHostId(UUID hostId, Pageable pageable);
}
