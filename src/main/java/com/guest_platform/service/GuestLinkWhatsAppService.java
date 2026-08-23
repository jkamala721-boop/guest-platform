package com.guest_platform.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.GuestLinkEmailRequest;
import com.guest_platform.dto.NotificationResponse;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.NotificationChannel;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;

/** Validates a raw link at send time and delegates transport to NotificationService. */
@Service
public class GuestLinkWhatsAppService {

    private final BookingRepository bookingRepository;
    private final GuestLinkService guestLinkService;
    private final NotificationService notificationService;
    private final String publicBaseUrl;

    public GuestLinkWhatsAppService(BookingRepository bookingRepository, GuestLinkService guestLinkService,
            NotificationService notificationService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.bookingRepository = bookingRepository;
        this.guestLinkService = guestLinkService;
        this.notificationService = notificationService;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Transactional
    public NotificationResponse send(UUID hostId, UUID bookingId, GuestLinkEmailRequest request) {
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        var guestLink = guestLinkService.resolveUsableGuestLink(request.token());
        if (!guestLink.getBooking().getId().equals(booking.getId())) {
            throw new ResourceNotFoundException("Guest link was not found");
        }
        String guestName = booking.getGuest() == null ? "Guest" : booking.getGuest().getFullName();
        String url = publicBaseUrl + "/guest/" + URLEncoder.encode(request.token(), StandardCharsets.UTF_8);
        return notificationService.sendGuestLink(hostId, bookingId, NotificationChannel.WHATSAPP,
                List.of(guestName, booking.getProperty().getName(), booking.getCheckInDate().toString(),
                        booking.getCheckOutDate().toString(), url));
    }
}
