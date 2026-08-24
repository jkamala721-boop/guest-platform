package com.guest_platform.service.payment;

import java.math.BigDecimal;
import java.util.UUID;

import com.guest_platform.entity.PaymentProvider;

public interface PaymentProviderAdapter {

    PaymentProvider provider();

    PaymentInitiation initiate(PaymentInitiationRequest request);

    record PaymentInitiation(String providerReference, String nextAction) {
    }

    record PaymentInitiationRequest(UUID paymentId, UUID bookingId, BigDecimal amount, String currency,
            String returnUrl, String customerEmail) {
    }
}
