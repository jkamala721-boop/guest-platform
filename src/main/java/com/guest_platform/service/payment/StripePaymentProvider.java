package com.guest_platform.service.payment;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.guest_platform.entity.PaymentProvider;

@Component
public class StripePaymentProvider implements PaymentProviderAdapter {

    private final String mode;

    public StripePaymentProvider(@Value("${app.payments.stripe.mode:mock}") String mode) {
        this.mode = mode;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public PaymentInitiation initiate(BigDecimal amount, String currency) {
        if (!"mock".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Stripe live initiation is not configured");
        }
        return new PaymentInitiation("STRIPE-MOCK-" + UUID.randomUUID(),
                "Complete the Stripe payment for " + amount.toPlainString() + " "
                        + currency.toUpperCase(Locale.ROOT));
    }
}
