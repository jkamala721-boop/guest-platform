package com.guest_platform.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.Host;

public interface HostRepository extends JpaRepository<Host, UUID> {
    Optional<Host> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
