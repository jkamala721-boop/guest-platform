package com.guest_platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.PublicGuestLinkResponse;
import com.guest_platform.dto.PublicGuestRegistrationRequest;
import com.guest_platform.dto.PublicReceiptResponse;
import com.guest_platform.service.GuestLinkService;
import com.guest_platform.service.ReceiptService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/public/guest")
public class PublicGuestLinkController {

    private final GuestLinkService guestLinkService;
    private final ReceiptService receiptService;

    public PublicGuestLinkController(GuestLinkService guestLinkService, ReceiptService receiptService) {
        this.guestLinkService = guestLinkService;
        this.receiptService = receiptService;
    }

    @GetMapping("/{token}")
    public PublicGuestLinkResponse resolve(@PathVariable String token) {
        return guestLinkService.resolvePublic(token);
    }

    @PutMapping("/{token}/registration")
    public org.springframework.http.ResponseEntity<Void> register(@PathVariable String token,
            @Valid @RequestBody PublicGuestRegistrationRequest request) {
        guestLinkService.updateGuestRegistration(token, request);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/{token}/receipt")
    public PublicReceiptResponse receipt(@PathVariable String token) {
        return receiptService.getPublic(token);
    }
}
