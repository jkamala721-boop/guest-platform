package com.guest_platform.service.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.guest_platform.entity.PaymentProvider;

/** Initializes Paystack-hosted checkout; secret-key use remains server-side. */
@Component
public class PaystackPaymentProvider implements PaymentProviderAdapter {

    private final String mode;
    private final String secretKey;
    private final PaystackApiClient paystackApiClient;

    public PaystackPaymentProvider(@Value("${app.payments.paystack.mode:mock}") String mode,
            @Value("${app.payments.paystack.secret-key:}") String secretKey, PaystackApiClient paystackApiClient) {
        this.mode = mode;
        this.secretKey = secretKey;
        this.paystackApiClient = paystackApiClient;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYSTACK;
    }

    @Override
    public PaymentInitiation initiate(PaymentInitiationRequest request) {
        if ("mock".equalsIgnoreCase(mode)) {
            return new PaymentInitiation("PAYSTACK-MOCK-" + UUID.randomUUID(),
                    "Complete the Paystack payment for " + request.amount().toPlainString() + " "
                            + request.currency().toUpperCase(Locale.ROOT));
        }
        if (!"live".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Paystack payment mode is invalid");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack integration is not configured");
        }
        if (request.customerEmail() == null || request.customerEmail().isBlank()) {
            throw new IllegalArgumentException("A guest email is required for Paystack payment");
        }
        if (request.paystackSubaccountCode() == null || request.paystackSubaccountCode().isBlank()
                || request.paystackTransactionCharge() == null) {
            throw new IllegalStateException("Paystack payout settings are not configured");
        }

        String reference = "HV-" + request.paymentId();
        PaystackApiClient.InitializeResult result = paystackApiClient.initialize(new PaystackApiClient.InitializeRequest(
                request.customerEmail(), String.valueOf(toMinorUnits(request.amount())),
                request.currency().toUpperCase(Locale.ROOT), reference, request.returnUrl(),
                "{\"paymentId\":\"" + request.paymentId() + "\",\"bookingId\":\"" + request.bookingId()
                        + "\"}", request.paystackSubaccountCode(),
                toMinorUnits(request.paystackTransactionCharge()), "account"));
        if (!reference.equals(result.reference())) {
            throw new IllegalStateException("Paystack returned an unexpected transaction reference");
        }
        return new PaymentInitiation(result.reference(), result.authorizationUrl());
    }

    static long toMinorUnits(BigDecimal amount) {
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Paystack amount must use two decimal places", exception);
        }
    }
}
