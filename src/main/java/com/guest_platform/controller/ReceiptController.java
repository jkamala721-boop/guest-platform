package com.guest_platform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.ReceiptResponse;
import com.guest_platform.dto.ReceiptDocument;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.ReceiptService;

@RestController
@RequestMapping
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/api/receipts")
    public List<ReceiptResponse> list(Authentication authentication) {
        return receiptService.list(CurrentHost.id(authentication));
    }

    @GetMapping("/api/receipts/{receiptId}")
    public ReceiptResponse get(Authentication authentication, @PathVariable UUID receiptId) {
        return receiptService.get(CurrentHost.id(authentication), receiptId);
    }

    @GetMapping("/api/bookings/{bookingId}/receipt")
    public ReceiptResponse getForBooking(Authentication authentication, @PathVariable UUID bookingId) {
        return receiptService.getForBooking(CurrentHost.id(authentication), bookingId);
    }

    @GetMapping(value = "/api/bookings/{bookingId}/receipt/document", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> documentForBooking(Authentication authentication, @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "false") boolean download) {
        return document(receiptService.documentForBooking(CurrentHost.id(authentication), bookingId), download);
    }

    private ResponseEntity<String> document(ReceiptDocument receipt, boolean download) {
        String disposition = (download ? "attachment" : "inline") + "; filename=\"" + receipt.filename() + "\"";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.TEXT_HTML).body(receipt.html());
    }
}
