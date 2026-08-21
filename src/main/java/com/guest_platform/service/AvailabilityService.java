package com.guest_platform.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.AvailabilityResponse;
import com.guest_platform.dto.AvailabilityCalendarResponse;
import com.guest_platform.dto.UnavailableDateRangeResponse;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingExtensionStatus;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.PropertyRepository;
import com.guest_platform.repository.BookingExtensionRepository;

@Service
public class AvailabilityService {

    private static final Set<BookingStatus> BLOCKING_STATUSES = EnumSet.of(
            BookingStatus.PENDING_CONFIRMATION, BookingStatus.PENDING_PAYMENT,
            BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final BookingExtensionRepository bookingExtensionRepository;

    public AvailabilityService(BookingRepository bookingRepository, PropertyRepository propertyRepository,
            BookingExtensionRepository bookingExtensionRepository) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.bookingExtensionRepository = bookingExtensionRepository;
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

    @Transactional(readOnly = true)
    public AvailabilityCalendarResponse getCalendar(UUID hostId, UUID propertyId, LocalDate from, LocalDate to) {
        propertyRepository.findByIdAndHostId(propertyId, hostId).orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
        return calendar(propertyId, from, to);
    }

    @Transactional(readOnly = true)
    public AvailabilityCalendarResponse getPublicCalendar(UUID propertyId, LocalDate from, LocalDate to) {
        return calendar(propertyId, from, to);
    }

    /**
     * Phase 6 uses a one-day window after checkout as the conservative extension
     * availability signal. Phase 7 will define actual extension durations.
     */
    @Transactional(readOnly = true)
    public boolean isAvailableForExtension(UUID propertyId, LocalDate checkoutDate, UUID currentBookingId) {
        return isAvailable(propertyId, checkoutDate, checkoutDate.plusDays(1), currentBookingId);
    }

    public boolean isAvailable(UUID propertyId, LocalDate checkInDate, LocalDate checkOutDate,
            UUID excludedBookingId) {
        boolean conflict = excludedBookingId == null
                ? bookingRepository.existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThan(
                        propertyId, BLOCKING_STATUSES, checkOutDate, checkInDate)
                : bookingRepository.existsByPropertyIdAndStatusInAndCheckInDateLessThanAndCheckOutDateGreaterThanAndIdNot(
                        propertyId, BLOCKING_STATUSES, checkOutDate, checkInDate, excludedBookingId);
        boolean reservedExtension = bookingExtensionRepository
                .existsByBookingPropertyIdAndStatusAndExpiresAtAfterAndOriginalCheckOutDateLessThanAndRequestedCheckOutDateGreaterThan(
                        propertyId, BookingExtensionStatus.PENDING_PAYMENT, java.time.Instant.now(), checkOutDate, checkInDate);
        return !conflict && !reservedExtension;
    }

    private AvailabilityCalendarResponse calendar(UUID propertyId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        List<UnavailableDateRangeResponse> ranges = new ArrayList<>();
        bookingRepository.findAll().stream().filter(booking -> booking.getProperty().getId().equals(propertyId))
                .filter(booking -> booking.getStatus().blocksAvailability())
                .filter(booking -> booking.getCheckInDate().isBefore(to) && booking.getCheckOutDate().isAfter(from))
                .map(booking -> new UnavailableDateRangeResponse(booking.getCheckInDate(), booking.getCheckOutDate()))
                .forEach(ranges::add);
        bookingExtensionRepository.findAll().stream()
                .filter(extension -> extension.isPendingAt(java.time.Instant.now()))
                .filter(extension -> extension.getBooking().getProperty().getId().equals(propertyId))
                .filter(extension -> extension.getOriginalCheckOutDate().isBefore(to)
                        && extension.getRequestedCheckOutDate().isAfter(from))
                .map(extension -> new UnavailableDateRangeResponse(extension.getOriginalCheckOutDate(),
                        extension.getRequestedCheckOutDate()))
                .forEach(ranges::add);
        return new AvailabilityCalendarResponse(propertyId, from, to, ranges);
    }

    private void validateDateRange(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        }
    }
}
