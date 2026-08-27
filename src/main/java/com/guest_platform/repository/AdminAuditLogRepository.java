package com.guest_platform.repository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.guest_platform.entity.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    List<AdminAuditLog> findAllByOrderByCreatedAtAsc();
    @EntityGraph(attributePaths="adminUser")
    @Query("select a from AdminAuditLog a where a.entityType='HOST' and a.entityId=:hostId and a.action in ('HOST_SUSPENDED','HOST_REACTIVATED')")
    Page<AdminAuditLog> findHostAccountTimeline(@Param("hostId") String hostId,Pageable pageable);
    @EntityGraph(attributePaths="adminUser")
    @Query("select a from AdminAuditLog a where a.entityType='HOST_PAYOUT' and a.entityId in :payoutIds and a.action in ('HOST_PAYOUT_MANUAL_CONFIRMED','HOST_PAYOUT_MARKED_FAILED') order by a.createdAt desc")
    List<AdminAuditLog> findPayoutActions(@Param("payoutIds") Collection<String> payoutIds);
}
