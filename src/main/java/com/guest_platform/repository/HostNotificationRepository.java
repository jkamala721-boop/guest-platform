package com.guest_platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.guest_platform.entity.HostNotification;
import com.guest_platform.entity.HostNotificationStatus;
import com.guest_platform.entity.HostNotificationType;

public interface HostNotificationRepository extends JpaRepository<HostNotification, UUID> {
    boolean existsByEventKey(String eventKey);
    Optional<HostNotification> findByEventKey(String eventKey);
    List<HostNotification> findTop50ByStatusOrderByCreatedAtAsc(HostNotificationStatus status);
    long countByBookingIdAndType(UUID bookingId, HostNotificationType type);
    long countByPayoutIdAndType(UUID payoutId, HostNotificationType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from HostNotification notification where notification.id = :id")
    Optional<HostNotification> findForUpdateById(@Param("id") UUID id);
}
