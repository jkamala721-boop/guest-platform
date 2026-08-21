package com.guest_platform.service.payment;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.guest_platform.entity.PaymentProvider;

@Component
public class MpesaPaymentProvider implements PaymentProviderAdapter {

    private final String mode;

    public MpesaPaymentProvider(@Value("${app.payments.mpesa.mode:mock}") String mode) {
        this.mode = mode;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MPESA;
    }

    @Override
    public PaymentInitiation initiate(BigDecimal amount, String currency) {
        if (!"mock".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("M-Pesa live initiation is not configured");
        }
        return new PaymentInitiation("MPESA-MOCK-" + UUID.randomUUID(),
                "Complete the M-Pesa prompt for " + amount.toPlainString() + " "
                        + currency.toUpperCase(Locale.ROOT));
    }
}
