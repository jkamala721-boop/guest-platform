package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.guest_platform.dto.PaymentWebhookRequest;
import com.guest_platform.exception.WebhookAuthenticationException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentWebhookVerifier {

    private static final long STRIPE_TIMESTAMP_TOLERANCE_SECONDS = 300;

    private final String mpesaWebhookSecret;
    private final String stripeWebhookSecret;
    private final ObjectMapper objectMapper;

    public PaymentWebhookVerifier(@Value("${app.payments.mpesa.webhook-secret:}") String mpesaWebhookSecret,
            @Value("${app.payments.stripe.webhook-secret:}") String stripeWebhookSecret, ObjectMapper objectMapper) {
        this.mpesaWebhookSecret = mpesaWebhookSecret;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.objectMapper = objectMapper;
    }

    public PaymentWebhookRequest verifyMpesa(String suppliedSecret, String payload) {
        if (mpesaWebhookSecret.isBlank() || suppliedSecret == null
                || !MessageDigest.isEqual(mpesaWebhookSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookAuthenticationException();
        }
        return parse(payload);
    }

    public PaymentWebhookRequest verifyStripe(String signatureHeader, String payload) {
        if (stripeWebhookSecret.isBlank() || signatureHeader == null) {
            throw new WebhookAuthenticationException();
        }
        String timestamp = value(signatureHeader, "t");
        if (timestamp == null || !withinTolerance(timestamp)) {
            throw new WebhookAuthenticationException();
        }
        byte[] expected = hmac(timestamp + "." + payload, stripeWebhookSecret);
        boolean matches = Arrays.stream(signatureHeader.split(","))
                .map(String::trim)
                .filter(part -> part.startsWith("v1="))
                .map(part -> part.substring(3))
                .anyMatch(signature -> MessageDigest.isEqual(expected, decodeHex(signature)));
        if (!matches) {
            throw new WebhookAuthenticationException();
        }
        return parse(payload);
    }

    private PaymentWebhookRequest parse(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentWebhookRequest.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid payment webhook payload");
        }
    }

    private boolean withinTolerance(String timestamp) {
        try {
            long epochSeconds = Long.parseLong(timestamp);
            return Math.abs(Instant.now().getEpochSecond() - epochSeconds) <= STRIPE_TIMESTAMP_TOLERANCE_SECONDS;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String value(String header, String name) {
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(part -> part.startsWith(name + "="))
                .map(part -> part.substring(name.length() + 1))
                .findFirst()
                .orElse(null);
    }

    private byte[] hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Webhook signature verifier is unavailable", exception);
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
