package com.guest_platform.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.guest_platform.entity.Host;

public interface HostRepository extends JpaRepository<Host, UUID> {
    Optional<Host> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select host from Host host where host.id = :id")
    Optional<Host> findForUpdateById(@Param("id") UUID id);
}
