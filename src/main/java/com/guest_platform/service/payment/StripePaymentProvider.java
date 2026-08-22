package com.guest_platform.service.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.guest_platform.entity.PaymentProvider;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

/** Creates either safe local mock references or Stripe-hosted Checkout Sessions. */
@Component
public class StripePaymentProvider implements PaymentProviderAdapter {

    private final String mode;
    private final String secretKey;

    public StripePaymentProvider(@Value("${app.payments.stripe.mode:mock}") String mode,
            @Value("${app.payments.stripe.secret-key:}") String secretKey) {
        this.mode = mode;
        this.secretKey = secretKey;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public PaymentInitiation initiate(PaymentInitiationRequest request) {
        if ("mock".equalsIgnoreCase(mode)) {
            return new PaymentInitiation("STRIPE-MOCK-" + UUID.randomUUID(),
                    "Complete the Stripe payment for " + request.amount().toPlainString() + " "
                            + request.currency().toUpperCase(Locale.ROOT));
        }
        if (!"live".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Stripe payment mode is invalid");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe integration is not configured");
        }

        try {
            Session session = new StripeClient(secretKey).v1().checkout().sessions().create(checkoutParams(request),
                    RequestOptions.builder().setIdempotencyKey(request.paymentId().toString()).build());
            if (session.getId() == null || session.getUrl() == null || session.getUrl().isBlank()) {
                throw new IllegalStateException("Stripe did not return a Checkout Session URL");
            }
            return new PaymentInitiation(session.getId(), session.getUrl());
        } catch (StripeException exception) {
            throw new IllegalStateException("Unable to create the Stripe Checkout Session", exception);
        }
    }

    private SessionCreateParams checkoutParams(PaymentInitiationRequest request) {
        String currency = request.currency().toLowerCase(Locale.ROOT);
        String paymentId = request.paymentId().toString();
        String bookingId = request.bookingId().toString();
        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(paymentId)
                .setSuccessUrl(request.returnUrl())
                .setCancelUrl(request.returnUrl())
                .putMetadata("paymentId", paymentId)
                .putMetadata("bookingId", bookingId)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("paymentId", paymentId)
                        .putMetadata("bookingId", bookingId)
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(toMinorUnits(request.amount()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Hostvero stay payment")
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private long toMinorUnits(BigDecimal amount) {
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Stripe amount must use two decimal places", exception);
        }
    }
}
