package com.guest_platform.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.PropertyResponse;
import com.guest_platform.dto.PropertyUpsertRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.Property;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.HostRepository;
import com.guest_platform.repository.PropertyRepository;

@Service
public class PropertyService {

    private final HostRepository hostRepository;
    private final PropertyRepository propertyRepository;

    public PropertyService(HostRepository hostRepository, PropertyRepository propertyRepository) {
        this.hostRepository = hostRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public PropertyResponse create(UUID hostId, PropertyUpsertRequest request) {
        Host host = findActiveHost(hostId);
        Property property = new Property(host);
        apply(property, request);
        return PropertyResponse.from(propertyRepository.save(property));
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> list(UUID hostId) {
        return propertyRepository.findAllByHostIdOrderByCreatedAtDesc(hostId).stream()
                .map(PropertyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyResponse get(UUID hostId, UUID propertyId) {
        return PropertyResponse.from(findOwnedProperty(hostId, propertyId));
    }

    @Transactional
    public PropertyResponse update(UUID hostId, UUID propertyId, PropertyUpsertRequest request) {
        Property property = findOwnedProperty(hostId, propertyId);
        apply(property, request);
        return PropertyResponse.from(property);
    }

    @Transactional
    public void deactivate(UUID hostId, UUID propertyId) {
        findOwnedProperty(hostId, propertyId).deactivate();
    }

    private Host findActiveHost(UUID hostId) {
        return hostRepository.findById(hostId)
                .filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
    }

    private Property findOwnedProperty(UUID hostId, UUID propertyId) {
        return propertyRepository.findByIdAndHostId(propertyId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
    }

    private void apply(Property property, PropertyUpsertRequest request) {
        property.update(request.name().trim(), request.propertyType(), request.address().trim(), request.mapsUrl().trim(),
                request.maxGuests(), request.defaultNightlyRate(), request.currency().toUpperCase(Locale.ROOT),
                request.checkInTime(), request.checkOutTime(), normalizeOptional(request.wifiName()),
                normalizeOptional(request.wifiPassword()), normalizeOptional(request.houseRules()),
                normalizeOptional(request.checkInInstructions()), normalizeOptional(request.contactPhone()), request.active());
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
