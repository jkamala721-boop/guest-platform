package com.guest_platform.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationStatus;
import com.guest_platform.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByHostIdOrderByScheduledAtDesc(UUID hostId);

    List<Notification> findAllByBookingIdAndHostIdOrderByScheduledAtDesc(UUID bookingId, UUID hostId);

    List<Notification> findAllByBookingId(UUID bookingId);

    Optional<Notification> findByIdAndHostId(UUID id, UUID hostId);

    Optional<Notification> findByBookingIdAndType(UUID bookingId, NotificationType type);

    List<Notification> findAllByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NotificationStatus status, Instant scheduledAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification where notification.id = :id")
    Optional<Notification> findForUpdateById(@Param("id") UUID id);
}
