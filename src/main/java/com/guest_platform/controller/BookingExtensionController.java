package com.guest_platform.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.guest_platform.dto.*;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.BookingExtensionService;
import com.guest_platform.service.PaymentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping
public class BookingExtensionController {
    private final BookingExtensionService extensionService; private final PaymentService paymentService;
    public BookingExtensionController(BookingExtensionService extensionService, PaymentService paymentService) { this.extensionService=extensionService; this.paymentService=paymentService; }
    @PostMapping("/api/bookings/{bookingId}/extend")
    public ResponseEntity<BookingExtensionResponse> extend(Authentication auth, @PathVariable UUID bookingId, @Valid @RequestBody ExtendStayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extensionService.extendForHost(CurrentHost.id(auth), bookingId, request.newCheckOutDate()));
    }
    @PostMapping("/api/bookings/{bookingId}/book-again")
    public ResponseEntity<BookAgainResponse> bookAgain(Authentication auth, @PathVariable UUID bookingId, @Valid @RequestBody BookAgainRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extensionService.bookAgainForHost(CurrentHost.id(auth), bookingId, request));
    }
    @PostMapping("/api/booking-extensions/{extensionId}/payments")
    public ResponseEntity<PaymentInitiationResponse> pay(Authentication auth, @PathVariable UUID extensionId, @Valid @RequestBody PaymentInitiateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.initiateExtension(CurrentHost.id(auth), extensionId, request));
    }
}
