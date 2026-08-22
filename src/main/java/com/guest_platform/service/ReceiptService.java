package com.guest_platform.service;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.PublicReceiptResponse;
import com.guest_platform.dto.ReceiptDocument;
import com.guest_platform.dto.ReceiptResponse;
import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;
import com.guest_platform.entity.Payment;
import com.guest_platform.entity.Receipt;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.ReceiptRepository;

@Service
public class ReceiptService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ReceiptRepository receiptRepository;
    private final GuestLinkService guestLinkService;

    public ReceiptService(ReceiptRepository receiptRepository, GuestLinkService guestLinkService) {
        this.receiptRepository = receiptRepository;
        this.guestLinkService = guestLinkService;
    }

    @Transactional
    public Receipt createForSucceededPayment(Payment payment) {
        return receiptRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> receiptRepository.save(new Receipt(payment.getHost(), payment.getBooking(), payment,
                        newReceiptNumber())));
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> list(UUID hostId) {
        return receiptRepository.findAllByHostIdOrderByIssuedAtDesc(hostId).stream().map(ReceiptResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(UUID hostId, UUID receiptId) {
        return ReceiptResponse.from(receiptRepository.findByIdAndHostId(receiptId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found")));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getForBooking(UUID hostId, UUID bookingId) {
        return ReceiptResponse.from(receiptRepository.findFirstByBookingIdAndHostIdOrderByIssuedAtDesc(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found")));
    }

    @Transactional(readOnly = true)
    public ReceiptDocument documentForBooking(UUID hostId, UUID bookingId) {
        Receipt receipt = receiptRepository.findFirstByBookingIdAndHostIdOrderByIssuedAtDesc(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found"));
        return documentFor(receipt);
    }

    @Transactional(readOnly = true)
    public PublicReceiptResponse getPublic(String token) {
        return PublicReceiptResponse.from(publicReceiptFor(token));
    }

    @Transactional(readOnly = true)
    public ReceiptDocument publicDocument(String token) {
        return documentFor(publicReceiptFor(token));
    }

    private Receipt publicReceiptFor(String token) {
        GuestLink guestLink = guestLinkService.resolveUsableGuestLink(token);
        if (guestLink.getState() != GuestLinkState.STAY_ACTIVE) {
            throw new ResourceNotFoundException("Receipt was not found");
        }
        return receiptRepository.findFirstByBookingIdAndHostIdOrderByIssuedAtDesc(guestLink.getBooking().getId(),
                guestLink.getBooking().getHost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found"));
    }

    private ReceiptDocument documentFor(Receipt receipt) {
        var booking = receipt.getBooking();
        var payment = receipt.getPayment();
        String guestName = booking.getGuest() == null ? "Guest" : booking.getGuest().getFullName();
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Hostvero receipt %s</title>
                <style>body{font-family:Arial,sans-serif;color:#1f2937;max-width:720px;margin:40px auto;padding:0 24px}header{border-bottom:3px solid #14b8a6;margin-bottom:28px;padding-bottom:16px}h1{color:#0d47a1;margin:0 0 6px}table{border-collapse:collapse;width:100%%}th,td{border-bottom:1px solid #e5e7eb;padding:12px 0;text-align:left}th{color:#4b5563;width:38%%}.status{color:#047857;font-weight:bold}</style>
                </head><body><header><h1>Hostvero</h1><div>Payment receipt</div></header>
                <table><tr><th>Receipt number</th><td>%s</td></tr><tr><th>Property</th><td>%s</td></tr><tr><th>Guest</th><td>%s</td></tr><tr><th>Check-in</th><td>%s</td></tr><tr><th>Check-out</th><td>%s</td></tr><tr><th>Payment provider</th><td>%s</td></tr><tr><th>Amount paid</th><td>%s %s</td></tr><tr><th>Payment status</th><td class="status">%s</td></tr><tr><th>Issued</th><td>%s</td></tr></table>
                </body></html>
                """.formatted(escape(receipt.getReceiptNumber()), escape(receipt.getReceiptNumber()),
                escape(booking.getProperty().getName()), escape(guestName), booking.getCheckInDate(),
                booking.getCheckOutDate(), escape(payment.getProvider().name()), receipt.getAmount().toPlainString(),
                escape(receipt.getCurrency()), escape(payment.getStatus().name()),
                DateTimeFormatter.ISO_INSTANT.format(receipt.getIssuedAt()));
        return new ReceiptDocument("hostvero-receipt-" + receipt.getReceiptNumber() + ".html", html);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String newReceiptNumber() {
        byte[] bytes = new byte[12];
        SECURE_RANDOM.nextBytes(bytes);
        return "HV-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
