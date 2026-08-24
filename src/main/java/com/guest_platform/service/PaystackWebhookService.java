package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.WebhookAuthenticationException;
import com.guest_platform.service.payment.PaystackApiClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates Paystack HMAC-SHA512 webhooks and verifies live transactions server-side. */
@Service
public class PaystackWebhookService {

    private final String mode;
    private final String secretKey;
    private final ObjectMapper objectMapper;
    private final PaystackApiClient paystackApiClient;
    private final PaymentService paymentService;

    public PaystackWebhookService(@Value("${app.payments.paystack.mode:mock}") String mode,
            @Value("${app.payments.paystack.secret-key:}") String secretKey, ObjectMapper objectMapper,
            PaystackApiClient paystackApiClient, PaymentService paymentService) {
        this.mode = mode;
        this.secretKey = secretKey;
        this.objectMapper = objectMapper;
        this.paystackApiClient = paystackApiClient;
        this.paymentService = paymentService;
    }

    public void process(String signature, String payload) {
        verifySignature(signature, payload);
        JsonNode root = parse(payload);
        if (!"charge.success".equals(root.path("event").asText())) {
            return;
        }
        JsonNode data = root.path("data");
        String reference = requiredText(data, "reference");
        long amountMinor = requiredLong(data, "amount");
        String currency = requiredText(data, "currency");
        if (!"success".equalsIgnoreCase(requiredText(data, "status"))) {
            return;
        }

        if ("live".equalsIgnoreCase(mode)) {
            PaystackApiClient.Verification verified = paystackApiClient.verify(reference);
            if (!reference.equals(verified.reference()) || amountMinor != verified.amountMinor()
                    || !currency.equalsIgnoreCase(verified.currency())) {
                throw new ConflictException("Paystack transaction did not match the booking");
            }
        } else if (!"mock".equalsIgnoreCase(mode)) {
            throw new WebhookAuthenticationException();
        }

        JsonNode metadata = metadata(data);
        paymentService.processVerifiedPaystackWebhook(new PaymentService.PaystackWebhookPayment(
                uuid(metadata, "paymentId"), uuid(metadata, "bookingId"), reference,
                "PAYSTACK-" + requiredText(data, "id"), amountMinor, currency));
    }

    private void verifySignature(String signature, String payload) {
        if (secretKey == null || secretKey.isBlank() || signature == null
                || !MessageDigest.isEqual(hmac(payload), decodeHex(signature.trim()))) {
            throw new WebhookAuthenticationException();
        }
    }

    private JsonNode parse(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.isMissingNode() || root.isNull()) {
                throw new IllegalArgumentException("Invalid Paystack webhook payload");
            }
            return root;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid Paystack webhook payload");
        }
    }

    private JsonNode metadata(JsonNode data) {
        JsonNode metadata = data.path("metadata");
        if (metadata.isTextual()) {
            try {
                metadata = objectMapper.readTree(metadata.textValue());
            } catch (JacksonException exception) {
                throw new IllegalArgumentException("Invalid Paystack webhook payload");
            }
        }
        if (metadata.isMissingNode() || metadata.isNull()) {
            throw new IllegalArgumentException("Invalid Paystack webhook payload");
        }
        return metadata;
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(requiredText(node, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Paystack webhook payload");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid Paystack webhook payload");
        }
        return value;
    }

    private long requiredLong(JsonNode node, String field) {
        if (!node.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("Invalid Paystack webhook payload");
        }
        return node.path(field).longValue();
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Paystack webhook signature verifier is unavailable", exception);
        }
    }

    private byte[] decodeHex(String value) {
        try {
            return java.util.HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }
}
