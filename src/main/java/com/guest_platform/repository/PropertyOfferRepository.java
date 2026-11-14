package com.guest_platform.repository;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.guest_platform.entity.PropertyOffer;

public interface PropertyOfferRepository extends JpaRepository<PropertyOffer, UUID> {
    Optional<PropertyOffer> findByTokenHash(String hash);
    boolean existsByTokenHash(String hash);
    List<PropertyOffer> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PropertyOffer o where o.id = :id")
    Optional<PropertyOffer> findForUpdate(@Param("id") UUID id);
}
