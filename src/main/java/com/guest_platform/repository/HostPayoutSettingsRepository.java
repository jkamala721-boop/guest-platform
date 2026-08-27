package com.guest_platform.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.guest_platform.entity.HostPayoutSettings;

public interface HostPayoutSettingsRepository extends JpaRepository<HostPayoutSettings, UUID> {

    Optional<HostPayoutSettings> findByHostId(UUID hostId);
    List<HostPayoutSettings> findAllByHostIdIn(Collection<UUID> hostIds);
}
