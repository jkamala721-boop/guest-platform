package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.entity.PaymentStatus;

public record PaymentResponse(UUID id, UUID bookingId, PaymentProvider provider, String providerReference,
        BigDecimal amount, BigDecimal bookingAmount, BigDecimal serviceFee, BigDecimal chargedAmount, String currency,
        BigDecimal processorFee, BigDecimal hostPayoutAmount, BigDecimal hostveroNetAmount, String providerChannel,
        PaymentStatus status,
        Instant paidAt, Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBooking().getId(), payment.getProvider(),
                payment.getProviderReference(), payment.getAmount(), payment.getBookingAmount(), payment.getServiceFee(),
                payment.getAmount(), payment.getCurrency(), payment.getProcessorFee(), payment.getHostPayoutAmount(),
                payment.getHostveroNetAmount(), payment.getProviderChannel(), payment.getStatus(),
                payment.getPaidAt(), payment.getCreatedAt());
    }
}
