package com.guest_platform.service.payment;

import java.math.BigDecimal;

import com.guest_platform.entity.PaymentProvider;

public interface PaymentProviderAdapter {

    PaymentProvider provider();

    PaymentInitiation initiate(BigDecimal amount, String currency);

    record PaymentInitiation(String providerReference, String nextAction) {
    }
}
