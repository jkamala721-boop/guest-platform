package com.guest_platform.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.HostResponse;
import com.guest_platform.dto.UpdateProfileRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.HostRepository;

@Service
public class HostProfileService {

    private final HostRepository hostRepository;

    public HostProfileService(HostRepository hostRepository) {
        this.hostRepository = hostRepository;
    }

    @Transactional(readOnly = true)
    public HostResponse getProfile(UUID hostId) {
        return HostResponse.from(findActiveHost(hostId));
    }

    @Transactional
    public HostResponse updateProfile(UUID hostId, UpdateProfileRequest request) {
        Host host = findActiveHost(hostId);
        host.updateProfile(request.fullName().trim(), normalizeOptional(request.phone()));
        return HostResponse.from(host);
    }

    private Host findActiveHost(UUID hostId) {
        return hostRepository.findById(hostId)
                .filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
