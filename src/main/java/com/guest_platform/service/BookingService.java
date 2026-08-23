package com.guest_platform.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.BookingCreateRequest;
import com.guest_platform.dto.BookingResponse;
import com.guest_platform.dto.BookingUpdateRequest;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.entity.Guest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.Property;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.HostRepository;
import com.guest_platform.repository.GuestRepository;
import com.guest_platform.repository.PropertyRepository;

@Service
public class BookingService {

    private final HostRepository hostRepository;
    private final PropertyRepository propertyRepository;
    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;
    private final AvailabilityService availabilityService;
    private final NotificationService notificationService;

    public BookingService(HostRepository hostRepository, PropertyRepository propertyRepository,
            GuestRepository guestRepository, BookingRepository bookingRepository,
            AvailabilityService availabilityService, NotificationService notificationService) {
        this.hostRepository = hostRepository;
        this.propertyRepository = propertyRepository;
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
        this.availabilityService = availabilityService;
        this.notificationService = notificationService;
    }

    @Transactional
    public BookingResponse create(UUID hostId, BookingCreateRequest request) {
        Host host = findActiveHost(hostId);

        Property property = findActiveOwnedProperty(
                hostId,
                request.propertyId()
        );

        property = propertyRepository.findForUpdateById(property.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Property was not found")
                );

        BookingStatus status = request.status() == null
                ? BookingStatus.PENDING_CONFIRMATION
                : request.status();

        availabilityService.requireAvailableFor(
                status,
                property.getId(),
                request.checkInDate(),
                request.checkOutDate(),
                null
        );

        Booking booking = new Booking(host, property);
        if (request.guestId() != null) {
            Guest guest = guestRepository.findByIdAndHostId(request.guestId(), hostId)
                    .orElseThrow(() -> new ResourceNotFoundException("Guest was not found"));
            booking.assignGuest(guest);
        }

        apply(
                booking,
                property,
                request.checkInDate(),
                request.checkOutDate(),
                request.totalAmount(),
                request.currency(),
                status,
                request.notes()
        );

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.reconcileBooking(
                savedBooking.getId()
        );

        return BookingResponse.from(savedBooking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(UUID hostId) {
        return bookingRepository.findAllByHostIdOrderByCreatedAtDesc(hostId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID hostId, UUID bookingId) {
        return BookingResponse.from(findOwnedBooking(hostId, bookingId));
    }

    @Transactional
    public BookingResponse update(UUID hostId, UUID bookingId, BookingUpdateRequest request) {
        Booking booking = findOwnedBooking(hostId, bookingId);
        Property property = findActiveOwnedProperty(hostId, request.propertyId());
        property = propertyRepository.findForUpdateById(property.getId()).orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
        availabilityService.requireAvailableFor(request.status(), property.getId(), request.checkInDate(),
                request.checkOutDate(), booking.getId());
        apply(booking, property, request.checkInDate(), request.checkOutDate(), request.totalAmount(),
                request.currency(), request.status(), request.notes());
        notificationService.reconcileBooking(booking.getId());
        return BookingResponse.from(booking);
    }

    @Transactional
    public void cancel(UUID hostId, UUID bookingId) {
        Booking booking = findOwnedBooking(hostId, bookingId);
        booking.cancel();
        notificationService.cancelPendingForBooking(booking.getId());
    }

    private Host findActiveHost(UUID hostId) {
        return hostRepository.findById(hostId)
                .filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
    }

    private Property findActiveOwnedProperty(UUID hostId, UUID propertyId) {
        return propertyRepository.findByIdAndHostId(propertyId, hostId)
                .filter(Property::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
    }

    private Booking findOwnedBooking(UUID hostId, UUID bookingId) {
        return bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
    }

    private void apply(Booking booking, Property property, java.time.LocalDate checkInDate,
            java.time.LocalDate checkOutDate, java.math.BigDecimal totalAmount, String currency,
            BookingStatus status, String notes) {
        booking.update(property, checkInDate, checkOutDate, totalAmount,
                currency.toUpperCase(Locale.ROOT), status, normalizeOptional(notes));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
