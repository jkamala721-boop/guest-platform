package com.guest_platform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.NotificationResponse;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.NotificationService;

@RestController
@RequestMapping
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
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
}
