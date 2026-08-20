package com.guest_platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.Property;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
    List<Property> findAllByHostIdOrderByCreatedAtDesc(UUID hostId);
    Optional<Property> findByIdAndHostId(UUID id, UUID hostId);
}
