package com.guest_platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.Property;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
    List<Property> findAllByHostIdOrderByCreatedAtDesc(UUID hostId);
    Optional<Property> findByIdAndHostId(UUID id, UUID hostId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select property from Property property where property.id = :id")
    Optional<Property> findForUpdateById(@Param("id") UUID id);
}
