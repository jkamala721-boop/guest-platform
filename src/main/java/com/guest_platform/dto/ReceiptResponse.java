package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.guest_platform.entity.Receipt;

public record ReceiptResponse(UUID id, String receiptNumber, UUID bookingId, UUID paymentId,
        BigDecimal amount, String currency, Instant issuedAt) {

    public static ReceiptResponse from(Receipt receipt) {
        return new ReceiptResponse(receipt.getId(), receipt.getReceiptNumber(), receipt.getBooking().getId(),
                receipt.getPayment().getId(), receipt.getAmount(), receipt.getCurrency(), receipt.getIssuedAt());
    }
}
