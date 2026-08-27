package com.guest_platform.repository;

import java.time.Instant;
import java.util.Collection;
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
    @Query("select property.id as propertyId,property.name as name,property.propertyType as propertyType,property.active as active,property.address as address,property.mapsUrl as mapsUrl,property.createdAt as createdAt from Property property where property.host.id=:hostId order by property.createdAt desc")
    List<AdminPropertyView> findAdminViewByHostId(@Param("hostId") UUID hostId);
    Optional<Property> findByIdAndHostId(UUID id, UUID hostId);
    boolean existsByHostIdAndActiveTrue(UUID hostId);
    @Query("select property.host.id as hostId,count(property) as totalCount,sum(case when property.active=true then 1 else 0 end) as activeCount,max(property.updatedAt) as lastActivityAt from Property property where property.host.id in :hostIds group by property.host.id")
    List<HostPropertySummary> summarizeByHostIds(@Param("hostIds") Collection<UUID> hostIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select property from Property property where property.id = :id")
    Optional<Property> findForUpdateById(@Param("id") UUID id);
    interface HostPropertySummary { UUID getHostId(); Long getTotalCount(); Long getActiveCount(); Instant getLastActivityAt(); }
    interface AdminPropertyView { UUID getPropertyId(); String getName(); com.guest_platform.entity.PropertyType getPropertyType(); boolean isActive(); String getAddress(); String getMapsUrl(); Instant getCreatedAt(); }
}
