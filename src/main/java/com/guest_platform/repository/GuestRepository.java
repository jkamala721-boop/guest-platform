package com.guest_platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.guest_platform.entity.Guest;

public interface GuestRepository extends JpaRepository<Guest, UUID> {
    List<Guest> findAllByHostIdOrderByCreatedAtDesc(UUID hostId);
    Optional<Guest> findByIdAndHostId(UUID id, UUID hostId);

    @Query("""
            select guest from Guest guest
            where guest.host.id = :hostId
              and (coalesce(:query, '') = '' or lower(guest.fullName) like lower(concat('%', :query, '%'))
                   or lower(guest.email) like lower(concat('%', :query, '%'))
                   or guest.phone like concat('%', :query, '%'))
              and (coalesce(:nationality, '') = '' or lower(guest.nationality) = lower(:nationality))
              and (coalesce(:idType, '') = '' or lower(guest.idType) = lower(:idType))
            order by guest.createdAt desc
            """)
    List<Guest> findAllForHost(@Param("hostId") UUID hostId, @Param("query") String query,
            @Param("nationality") String nationality, @Param("idType") String idType);
}
