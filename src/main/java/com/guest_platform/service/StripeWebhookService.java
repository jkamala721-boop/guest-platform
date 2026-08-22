package com.guest_platform.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.guest_platform.dto.PaymentWebhookRequest;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.exception.WebhookAuthenticationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Verifies Stripe's raw webhook payload and maps supported events to internal payments. */
@Service
public class StripeWebhookService {

    private final String mode;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final PaymentWebhookVerifier paymentWebhookVerifier;
    private final PaymentService paymentService;

    public StripeWebhookService(@Value("${app.payments.stripe.mode:mock}") String mode,
            @Value("${app.payments.stripe.webhook-secret:}") String webhookSecret, ObjectMapper objectMapper,
            PaymentWebhookVerifier paymentWebhookVerifier, PaymentService paymentService) {
        this.mode = mode;
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
        this.paymentWebhookVerifier = paymentWebhookVerifier;
        this.paymentService = paymentService;
    }

    public void process(String signature, String payload) {
        if ("mock".equalsIgnoreCase(mode)) {
            PaymentWebhookRequest request = paymentWebhookVerifier.verifyStripe(signature, payload);
            paymentService.processVerifiedWebhook(PaymentProvider.STRIPE, request);
            return;
        }
        if (!"live".equalsIgnoreCase(mode) || webhookSecret == null || webhookSecret.isBlank() || signature == null) {
            throw new WebhookAuthenticationException();
        }
        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            PaymentService.StripeWebhookPayment payment = map(event, payload);
            if (payment != null) {
                paymentService.processVerifiedStripeWebhook(payment);
            }
        } catch (SignatureVerificationException exception) {
            throw new WebhookAuthenticationException();
        }
    }

    private PaymentService.StripeWebhookPayment map(Event event, String payload) {
        JsonNode object = dataObject(payload);
        String type = event.getType();
        if ("checkout.session.completed".equals(type) || "checkout.session.async_payment_succeeded".equals(type)) {
            if (!"paid".equalsIgnoreCase(object.path("payment_status").asText())) {
                return null;
            }
            return payment(event, object, PaymentService.StripePaymentOutcome.SUCCEEDED, object.path("id").asText(),
                    requiredLong(object, "amount_total"), requiredText(object, "currency"), null);
        }
        if ("payment_intent.succeeded".equals(type)) {
            return payment(event, object, PaymentService.StripePaymentOutcome.SUCCEEDED, null,
                    requiredLong(object, "amount_received"), requiredText(object, "currency"), null);
        }
        if ("checkout.session.async_payment_failed".equals(type) || "payment_intent.payment_failed".equals(type)) {
            return payment(event, object, PaymentService.StripePaymentOutcome.FAILED,
                    "checkout.session.async_payment_failed".equals(type) ? object.path("id").asText() : null,
                    null, null, failureReason(object));
        }
        if ("checkout.session.expired".equals(type) || "payment_intent.canceled".equals(type)) {
            return payment(event, object, PaymentService.StripePaymentOutcome.CANCELLED,
                    "checkout.session.expired".equals(type) ? object.path("id").asText() : null,
                    null, null, failureReason(object));
        }
        return null;
    }

    private PaymentService.StripeWebhookPayment payment(Event event, JsonNode object,
            PaymentService.StripePaymentOutcome outcome, String providerReference, Long amountMinor, String currency,
            String failureReason) {
        JsonNode metadata = object.path("metadata");
        return new PaymentService.StripeWebhookPayment(uuid(metadata, "paymentId"), uuid(metadata, "bookingId"),
                providerReference, requiredEventId(event), outcome, amountMinor, currency, failureReason);
    }

    private JsonNode dataObject(String payload) {
        try {
            JsonNode object = objectMapper.readTree(payload).path("data").path("object");
            if (object.isMissingNode() || object.isNull()) {
                throw new IllegalArgumentException("Invalid Stripe webhook payload");
            }
            return object;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid Stripe webhook payload");
        }
    }

    private UUID uuid(JsonNode metadata, String field) {
        try {
            return UUID.fromString(requiredText(metadata, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Stripe payment metadata");
        }
    }

    private String requiredEventId(Event event) {
        if (event.getId() == null || event.getId().isBlank()) {
            throw new IllegalArgumentException("Invalid Stripe webhook payload");
        }
        return event.getId();
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid Stripe webhook payload");
        }
        return value;
    }

    private Long requiredLong(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("Invalid Stripe webhook payload");
        }
        return node.path(field).longValue();
    }

    private String failureReason(JsonNode object) {
        String message = object.path("last_payment_error").path("message").asText();
        return message == null || message.isBlank() ? "Stripe payment was not completed" : message;
    }
}
