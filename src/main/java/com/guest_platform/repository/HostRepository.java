package com.guest_platform.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;

import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostAccountStatus;
import com.guest_platform.entity.HostVerificationStatus;

public interface HostRepository extends JpaRepository<Host, UUID> {
    Optional<Host> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select host from Host host where host.id = :id")
    Optional<Host> findForUpdateById(@Param("id") UUID id);

    @Query(value = """
            select host from Host host left join HostVerification verification on verification.host = host
            where (:q is null or lower(host.email) like :q or lower(host.fullName) like :q
                or lower(coalesce(host.phone, '')) like :q)
              and (:accountStatus is null or host.accountStatus = :accountStatus)
              and (:verificationStatus is null or verification.status = :verificationStatus
                or (:verificationStatus = com.guest_platform.entity.HostVerificationStatus.UNVERIFIED
                    and verification.id is null))
            """,
            countQuery = """
            select count(host) from Host host left join HostVerification verification on verification.host = host
            where (:q is null or lower(host.email) like :q or lower(host.fullName) like :q
                or lower(coalesce(host.phone, '')) like :q)
              and (:accountStatus is null or host.accountStatus = :accountStatus)
              and (:verificationStatus is null or verification.status = :verificationStatus
                or (:verificationStatus = com.guest_platform.entity.HostVerificationStatus.UNVERIFIED
                    and verification.id is null))
            """)
    Page<Host> searchAdminHosts(@Param("q") String q, @Param("accountStatus") HostAccountStatus accountStatus,
            @Param("verificationStatus") HostVerificationStatus verificationStatus, Pageable pageable);
}
