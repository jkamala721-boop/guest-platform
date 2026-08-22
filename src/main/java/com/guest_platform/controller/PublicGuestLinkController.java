package com.guest_platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.PublicGuestLinkResponse;
import com.guest_platform.dto.PublicGuestRegistrationRequest;
import com.guest_platform.dto.PublicReceiptResponse;
import com.guest_platform.dto.ReceiptDocument;
import com.guest_platform.dto.ExtendStayRequest;
import com.guest_platform.dto.BookAgainRequest;
import com.guest_platform.dto.BookAgainResponse;
import com.guest_platform.dto.BookingExtensionResponse;
import com.guest_platform.dto.PaymentInitiateRequest;
import com.guest_platform.dto.PaymentInitiationResponse;
import com.guest_platform.dto.AvailabilityCalendarResponse;
import com.guest_platform.service.BookingExtensionService;
import com.guest_platform.service.PaymentService;
import com.guest_platform.service.AvailabilityService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.guest_platform.service.GuestLinkService;
import com.guest_platform.service.ReceiptService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/public/guest")
public class PublicGuestLinkController {

    private final GuestLinkService guestLinkService;
    private final ReceiptService receiptService;
    private final BookingExtensionService extensionService;
    private final PaymentService paymentService;
    private final AvailabilityService availabilityService;

    public PublicGuestLinkController(GuestLinkService guestLinkService, ReceiptService receiptService,
            BookingExtensionService extensionService, PaymentService paymentService, AvailabilityService availabilityService) {
        this.guestLinkService = guestLinkService;
        this.receiptService = receiptService;
        this.extensionService = extensionService; this.paymentService = paymentService; this.availabilityService = availabilityService;
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

    @PostMapping("/{token}/payments")
    public org.springframework.http.ResponseEntity<PaymentInitiationResponse> initiatePayment(@PathVariable String token,
            @Valid @RequestBody PaymentInitiateRequest request) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(paymentService.initiateForGuestLink(guestLinkService.resolveUsableGuestLink(token), token, request));
    }

    @GetMapping("/{token}/receipt")
    public PublicReceiptResponse receipt(@PathVariable String token) {
        return receiptService.getPublic(token);
    }

    @GetMapping(value = "/{token}/receipt/document", produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
    public org.springframework.http.ResponseEntity<String> receiptDocument(@PathVariable String token,
            @RequestParam(defaultValue = "false") boolean download) {
        ReceiptDocument receipt = receiptService.publicDocument(token);
        String disposition = (download ? "attachment" : "inline") + "; filename=\"" + receipt.filename() + "\"";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(org.springframework.http.MediaType.TEXT_HTML).body(receipt.html());
    }

    @PostMapping("/{token}/extend")
    public org.springframework.http.ResponseEntity<BookingExtensionResponse> extend(@PathVariable String token, @Valid @RequestBody ExtendStayRequest request) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(extensionService.extendForGuest(guestLinkService.resolveUsableGuestLink(token), request.newCheckOutDate()));
    }
    @PostMapping("/{token}/book-again")
    public org.springframework.http.ResponseEntity<BookAgainResponse> bookAgain(@PathVariable String token, @Valid @RequestBody BookAgainRequest request) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(extensionService.bookAgainForGuest(guestLinkService.resolveUsableGuestLink(token), request));
    }
    @PostMapping("/{token}/extensions/{extensionId}/payments")
    public org.springframework.http.ResponseEntity<PaymentInitiationResponse> extensionPayment(@PathVariable String token, @PathVariable java.util.UUID extensionId, @Valid @RequestBody PaymentInitiateRequest request) {
        var link=guestLinkService.resolveUsableGuestLink(token);
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(paymentService.initiateExtension(extensionService.requireForGuest(link, extensionId), request));
    }
    @GetMapping("/{token}/availability")
    public AvailabilityCalendarResponse availability(@PathVariable String token, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var link=guestLinkService.resolveUsableGuestLink(token); return availabilityService.getPublicCalendar(link.getBooking().getProperty().getId(), from, to);
    }
}
