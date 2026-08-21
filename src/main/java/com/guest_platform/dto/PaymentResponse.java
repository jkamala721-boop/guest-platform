package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.entity.PaymentStatus;

public record PaymentResponse(UUID id, UUID bookingId, PaymentProvider provider, String providerReference,
        BigDecimal amount, String currency, PaymentStatus status, Instant paidAt, Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBooking().getId(), payment.getProvider(),
                payment.getProviderReference(), payment.getAmount(), payment.getCurrency(), payment.getStatus(),
                payment.getPaidAt(), payment.getCreatedAt());
    }
}
