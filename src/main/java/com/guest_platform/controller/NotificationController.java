package com.guest_platform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.NotificationResponse;
import com.guest_platform.dto.ManualNotificationRequest;
import com.guest_platform.dto.GuestLinkEmailRequest;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.GuestLinkEmailService;
import com.guest_platform.service.GuestLinkWhatsAppService;
import com.guest_platform.service.NotificationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping
public class NotificationController {

    private final NotificationService notificationService;
    private final GuestLinkEmailService guestLinkEmailService;
    private final GuestLinkWhatsAppService guestLinkWhatsAppService;

    public NotificationController(NotificationService notificationService, GuestLinkEmailService guestLinkEmailService,
            GuestLinkWhatsAppService guestLinkWhatsAppService) {
        this.notificationService = notificationService;
        this.guestLinkEmailService = guestLinkEmailService;
        this.guestLinkWhatsAppService = guestLinkWhatsAppService;
    }

    @GetMapping("/api/notifications")
    public List<NotificationResponse> list(Authentication authentication) {
        return notificationService.list(CurrentHost.id(authentication));
    }

    @GetMapping("/api/notifications/{notificationId}")
    public NotificationResponse get(Authentication authentication, @PathVariable UUID notificationId) {
        return notificationService.get(CurrentHost.id(authentication), notificationId);
    }

    @GetMapping("/api/bookings/{bookingId}/notifications")
    public List<NotificationResponse> listForBooking(Authentication authentication, @PathVariable UUID bookingId) {
        return notificationService.listForBooking(CurrentHost.id(authentication), bookingId);
    }

    @PostMapping("/api/bookings/{bookingId}/notifications/manual")
    public NotificationResponse sendManual(Authentication authentication, @PathVariable UUID bookingId,
            @Valid @RequestBody ManualNotificationRequest request) {
        return notificationService.sendManual(CurrentHost.id(authentication), bookingId, request);
    }

    @PostMapping("/api/bookings/{bookingId}/guest-link/email")
    public NotificationResponse sendGuestLinkEmail(Authentication authentication, @PathVariable UUID bookingId,
            @Valid @RequestBody GuestLinkEmailRequest request) {
        return guestLinkEmailService.send(CurrentHost.id(authentication), bookingId, request);
    }

    @PostMapping("/api/bookings/{bookingId}/guest-link/whatsapp")
    public NotificationResponse sendGuestLinkWhatsApp(Authentication authentication, @PathVariable UUID bookingId,
            @Valid @RequestBody GuestLinkEmailRequest request) {
        return guestLinkWhatsAppService.send(CurrentHost.id(authentication), bookingId, request);
    }
}
