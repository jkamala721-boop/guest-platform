package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.guest_platform.entity.Receipt;

public record PublicReceiptResponse(String receiptNumber, BigDecimal amount, String currency, Instant issuedAt) {

    public static PublicReceiptResponse from(Receipt receipt) {
        return new PublicReceiptResponse(receipt.getReceiptNumber(), receipt.getAmount(), receipt.getCurrency(),
                receipt.getIssuedAt());
    }
}
