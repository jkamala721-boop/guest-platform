package com.guest_platform.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.AdminSession;

public interface AdminSessionRepository extends JpaRepository<AdminSession, UUID> {
    Optional<AdminSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AdminSession session set session.revokedAt = :now where session.adminUser.id = :adminId and session.revokedAt is null")
    int revokeActiveByAdminId(@Param("adminId") UUID adminId, @Param("now") Instant now);
}

