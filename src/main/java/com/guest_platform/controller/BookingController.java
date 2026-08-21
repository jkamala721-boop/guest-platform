package com.guest_platform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.BookingCreateRequest;
import com.guest_platform.dto.BookingResponse;
import com.guest_platform.dto.BookingUpdateRequest;
import com.guest_platform.dto.GuestLinkCreateResponse;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.BookingService;
import com.guest_platform.service.GuestLinkService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final GuestLinkService guestLinkService;

    public BookingController(BookingService bookingService, GuestLinkService guestLinkService) {
        this.bookingService = bookingService;
        this.guestLinkService = guestLinkService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(Authentication authentication,
            @Valid @RequestBody BookingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.create(CurrentHost.id(authentication), request));
    }

    @GetMapping
    public List<BookingResponse> list(Authentication authentication) {
        return bookingService.list(CurrentHost.id(authentication));
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(Authentication authentication, @PathVariable UUID bookingId) {
        return bookingService.get(CurrentHost.id(authentication), bookingId);
    }

    @PutMapping("/{bookingId}")
    public BookingResponse update(Authentication authentication, @PathVariable UUID bookingId,
            @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.update(CurrentHost.id(authentication), bookingId, request);
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancel(Authentication authentication, @PathVariable UUID bookingId) {
        bookingService.cancel(CurrentHost.id(authentication), bookingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bookingId}/guest-link")
    public ResponseEntity<GuestLinkCreateResponse> createGuestLink(Authentication authentication,
            @PathVariable UUID bookingId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guestLinkService.rotate(CurrentHost.id(authentication), bookingId));
    }
}
