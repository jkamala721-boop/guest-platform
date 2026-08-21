package com.guest_platform.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.AvailabilityResponse;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.PropertyRepository;

@Service
public class AvailabilityService {

    private static final Set<BookingStatus> BLOCKING_STATUSES = EnumSet.of(
            BookingStatus.PENDING_CONFIRMATION, BookingStatus.PENDING_PAYMENT,
            BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;

    public AvailabilityService(BookingRepository bookingRepository, PropertyRepository propertyRepository) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID hostId, UUID propertyId, LocalDate checkInDate,
            LocalDate checkOutDate) {
        propertyRepository.findByIdAndHostId(propertyId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
        validateDateRange(checkInDate, checkOutDate);
        return new AvailabilityResponse(isAvailable(propertyId, checkInDate, checkOutDate, null),
                checkInDate, checkOutDate);
    }

    public void requireAvailableFor(BookingStatus status, UUID propertyId, LocalDate checkInDate,
            LocalDate checkOutDate, UUID excludedBookingId) {
        validateDateRange(checkInDate, checkOutDate);
        if (status.blocksAvailability() && !isAvailable(propertyId, checkInDate, checkOutDate, excludedBookingId)) {
            throw new ConflictException("Property is unavailable for the requested dates");
        }
    }

    /**
     * Phase 6 uses a one-day window after checkout as the conservative extension
     * availability signal. Phase 7 will define actual extension durations.
     */
    @Transactional(readOnly = true)
    public boolean isAvailableForExtension(UUID propertyId, LocalDate checkoutDate, UUID currentBookingId) {
        return isAvailable(propertyId, checkoutDate, checkoutDate.plusDays(1), currentBookingId);
    }

    private boolean isAvailable(UUID propertyId, LocalDate checkInDate, LocalDate checkOutDate,
            UUID excludedBookingId) {
        boolean conflict = excludedBookingId == null
                ? bookingRepository.existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThan(
                        propertyId, BLOCKING_STATUSES, checkOutDate, checkInDate)
                : bookingRepository.existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThanAndIdNot(
                        propertyId, BLOCKING_STATUSES, checkOutDate, checkInDate, excludedBookingId);
        return !conflict;
    }

    private void validateDateRange(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        }
    }
}
