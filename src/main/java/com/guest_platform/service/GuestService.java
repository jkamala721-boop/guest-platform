package com.guest_platform.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.GuestCreateRequest;
import com.guest_platform.dto.GuestListResponse;
import com.guest_platform.dto.GuestResponse;
import com.guest_platform.dto.GuestUpdateRequest;
import com.guest_platform.entity.Guest;
import com.guest_platform.entity.Host;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.GuestRepository;
import com.guest_platform.repository.HostRepository;

@Service
public class GuestService {

    private final HostRepository hostRepository;
    private final GuestRepository guestRepository;

    public GuestService(HostRepository hostRepository, GuestRepository guestRepository) {
        this.hostRepository = hostRepository;
        this.guestRepository = guestRepository;
    }

    @Transactional
    public GuestResponse create(UUID hostId, GuestCreateRequest request) {
        Guest guest = new Guest(findActiveHost(hostId));
        apply(guest, request.fullName(), request.phone(), request.email(), request.idType(), request.idNumber(),
                request.nationality(), request.whatsappNumber(), request.notes());
        return GuestResponse.from(guestRepository.save(guest));
    }

    @Transactional(readOnly = true)
    public List<GuestListResponse> list(UUID hostId, String query, String nationality, String idType) {
        return guestRepository.findAllForHost(hostId, normalizeOptional(query), normalizeOptional(nationality),
                normalizeOptional(idType)).stream()
                .map(GuestListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuestResponse get(UUID hostId, UUID guestId) {
        return GuestResponse.from(findOwnedGuest(hostId, guestId));
    }

    @Transactional
    public GuestResponse update(UUID hostId, UUID guestId, GuestUpdateRequest request) {
        Guest guest = findOwnedGuest(hostId, guestId);
        apply(guest, request.fullName(), request.phone(), request.email(), request.idType(), request.idNumber(),
                request.nationality(), request.whatsappNumber(), request.notes());
        return GuestResponse.from(guest);
    }

    @Transactional
    public void delete(UUID hostId, UUID guestId) {
        guestRepository.delete(findOwnedGuest(hostId, guestId));
    }

    private Host findActiveHost(UUID hostId) {
        return hostRepository.findById(hostId)
                .filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
    }

    private Guest findOwnedGuest(UUID hostId, UUID guestId) {
        return guestRepository.findByIdAndHostId(guestId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest was not found"));
    }

    private void apply(Guest guest, String fullName, String phone, String email, String idType, String idNumber,
            String nationality, String whatsappNumber, String notes) {
        guest.update(fullName.trim(), phone.trim(), email.trim().toLowerCase(Locale.ROOT), normalizeOptional(idType),
                normalizeOptional(idNumber), normalizeOptional(nationality), normalizeOptional(whatsappNumber),
                normalizeOptional(notes));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
