package com.guest_platform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.PaymentInitiateRequest;
import com.guest_platform.dto.PaymentInitiationResponse;
import com.guest_platform.dto.PaymentResponse;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.PaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/bookings/{bookingId}/payments")
    public ResponseEntity<PaymentInitiationResponse> initiate(Authentication authentication, @PathVariable UUID bookingId,
            @Valid @RequestBody PaymentInitiateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.initiate(CurrentHost.id(authentication), bookingId, request));
    }

    @GetMapping("/api/bookings/{bookingId}/payments")
    public List<PaymentResponse> listForBooking(Authentication authentication, @PathVariable UUID bookingId) {
        return paymentService.listForBooking(CurrentHost.id(authentication), bookingId);
    }

    @GetMapping("/api/payments/{paymentId}")
    public PaymentResponse get(Authentication authentication, @PathVariable UUID paymentId) {
        return paymentService.get(CurrentHost.id(authentication), paymentId);
    }
}
