package com.guest_platform.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.HostSession;

public interface HostSessionRepository extends JpaRepository<HostSession, java.util.UUID> {
    Optional<HostSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByTokenHash(String tokenHash);
}
