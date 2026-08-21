package com.guest_platform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.BookAgainRequest;
import com.guest_platform.dto.BookAgainResponse;
import com.guest_platform.dto.BookingExtensionResponse;
import com.guest_platform.dto.BookingResponse;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingExtension;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.entity.GuestLink;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingExtensionRepository;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.PropertyRepository;

@Service
public class BookingExtensionService {
    private final BookingRepository bookingRepository; private final PropertyRepository propertyRepository;
    private final BookingExtensionRepository extensionRepository; private final AvailabilityService availabilityService;
    private final NotificationService notificationService; private final GuestLinkService guestLinkService;
    private final long reservationMinutes;

    public BookingExtensionService(BookingRepository bookingRepository, PropertyRepository propertyRepository,
            BookingExtensionRepository extensionRepository, AvailabilityService availabilityService,
            NotificationService notificationService, GuestLinkService guestLinkService,
            @Value("${app.extensions.reservation-minutes:30}") long reservationMinutes) {
        if (reservationMinutes < 1 || reservationMinutes > 1440) throw new IllegalArgumentException("extension reservation minutes must be between 1 and 1440");
        this.bookingRepository=bookingRepository; this.propertyRepository=propertyRepository; this.extensionRepository=extensionRepository;
        this.availabilityService=availabilityService; this.notificationService=notificationService; this.guestLinkService=guestLinkService; this.reservationMinutes=reservationMinutes;
    }

    @Transactional
    public BookingExtensionResponse extendForHost(UUID hostId, UUID bookingId, LocalDate requestedCheckOut) {
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId).orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        return extend(booking, requestedCheckOut);
    }
    @Transactional
    public BookingExtensionResponse extendForGuest(GuestLink link, LocalDate requestedCheckOut) { return extend(link.getBooking(), requestedCheckOut); }
    private BookingExtensionResponse extend(Booking booking, LocalDate requestedCheckOut) {
        propertyRepository.findForUpdateById(booking.getProperty().getId()).orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
        if (!requestedCheckOut.isAfter(booking.getCheckOutDate())) throw new IllegalArgumentException("newCheckOutDate must be after the current checkOutDate");
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            availabilityService.requireAvailableFor(booking.getStatus(), booking.getProperty().getId(), booking.getCheckInDate(), requestedCheckOut, booking.getId());
            LocalDate originalCheckOut = booking.getCheckOutDate(); BigDecimal originalAmount = booking.getTotalAmount();
            BigDecimal added = nightlyAmount(booking, requestedCheckOut); booking.extendTo(requestedCheckOut, originalAmount.add(added));
            guestLinkService.synchronizeExpiryForBooking(booking); notificationService.reconcileBooking(booking.getId());
            return new BookingExtensionResponse(null, booking.getId(), originalCheckOut, requestedCheckOut,
                    Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(originalCheckOut, requestedCheckOut)),
                    originalAmount, added, booking.getTotalAmount(), booking.getCurrency(), null, null);
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.CHECKED_IN) throw new ConflictException("Booking cannot be extended in its current state");
        availabilityService.requireAvailableFor(booking.getStatus(), booking.getProperty().getId(), booking.getCheckOutDate(), requestedCheckOut, booking.getId());
        BookingExtension extension = extensionRepository.save(new BookingExtension(booking, requestedCheckOut, nightlyAmount(booking, requestedCheckOut), Instant.now().plusSeconds(reservationMinutes * 60)));
        return BookingExtensionResponse.from(extension);
    }
    @Transactional
    public BookAgainResponse bookAgainForHost(UUID hostId, UUID bookingId, BookAgainRequest request) {
        Booking source=bookingRepository.findByIdAndHostId(bookingId,hostId).orElseThrow(()->new ResourceNotFoundException("Booking was not found")); return bookAgain(source,request);
    }
    @Transactional
    public BookAgainResponse bookAgainForGuest(GuestLink link, BookAgainRequest request) { return bookAgain(link.getBooking(),request); }
    private BookAgainResponse bookAgain(Booking source, BookAgainRequest request) {
        propertyRepository.findForUpdateById(source.getProperty().getId()).orElseThrow(()->new ResourceNotFoundException("Property was not found"));
        availabilityService.requireAvailableFor(BookingStatus.PENDING_PAYMENT, source.getProperty().getId(), request.checkInDate(), request.checkOutDate(), null);
        Booking repeat=new Booking(source.getHost(),source.getProperty(),source.getGuest());
        BigDecimal amount=source.getProperty().getDefaultNightlyRate().multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(request.checkInDate(),request.checkOutDate())));
        repeat.update(source.getProperty(),source.getGuest(),request.checkInDate(),request.checkOutDate(),amount,source.getProperty().getCurrency(),BookingStatus.PENDING_PAYMENT,null);
        bookingRepository.save(repeat); notificationService.reconcileBooking(repeat.getId());
        return new BookAgainResponse(BookingResponse.from(repeat),guestLinkService.createForNewBooking(repeat));
    }
    @Transactional
    public boolean applyPaidExtension(BookingExtension extension) {
        propertyRepository.findForUpdateById(extension.getBooking().getProperty().getId()).orElseThrow(()->new ResourceNotFoundException("Property was not found"));
        extension.expireIfNecessary(Instant.now()); if (!extension.confirm()) return false;
        Booking booking=extension.getBooking(); booking.extendTo(extension.getRequestedCheckOutDate(),extension.getResultingTotalAmount());
        guestLinkService.synchronizeExpiryForBooking(booking); notificationService.reconcileBooking(booking.getId()); return true;
    }
    @Transactional public void failExtension(BookingExtension extension) { extension.fail(); }
    @Transactional(readOnly = true)
    public BookingExtension requireForGuest(GuestLink link, UUID extensionId) {
        return extensionRepository.findById(extensionId)
                .filter(extension -> extension.getBooking().getId().equals(link.getBooking().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Booking extension was not found"));
    }
    private BigDecimal nightlyAmount(Booking booking, LocalDate requestedCheckOut) { return booking.getProperty().getDefaultNightlyRate().multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(booking.getCheckOutDate(), requestedCheckOut))); }
}
