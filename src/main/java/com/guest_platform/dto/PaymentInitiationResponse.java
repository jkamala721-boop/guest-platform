package com.guest_platform.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.entity.PaymentStatus;

public record PaymentInitiationResponse(UUID id, PaymentProvider provider, String providerReference,
        BigDecimal amount, String currency, PaymentStatus status, String nextAction) {

    public static PaymentInitiationResponse from(Payment payment, String nextAction) {
        return new PaymentInitiationResponse(payment.getId(), payment.getProvider(), payment.getProviderReference(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus(), nextAction);
    }
}
