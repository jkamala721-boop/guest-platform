package com.guest_platform.service.payment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Server-side Paystack API boundary. It never logs credentials or provider response bodies. */
@Component
public class PaystackApiClient {

    private static final URI INITIALIZE_URI = URI.create("https://api.paystack.co/transaction/initialize");
    private static final URI SUBACCOUNT_URI = URI.create("https://api.paystack.co/subaccount");
    private static final String SUBACCOUNT_UPDATE_URI = "https://api.paystack.co/subaccount/";
    private static final String VERIFY_URI = "https://api.paystack.co/transaction/verify/";

    private final String secretKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PaystackApiClient(@Value("${app.payments.paystack.secret-key:}") String secretKey,
            ObjectMapper objectMapper) {
        this(secretKey, objectMapper, HttpClient.newHttpClient());
    }

    PaystackApiClient(String secretKey, ObjectMapper objectMapper, HttpClient httpClient) {
        this.secretKey = secretKey;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public InitializeResult initialize(InitializeRequest request) {
        JsonNode response = send(INITIALIZE_URI, "POST", write(request));
        JsonNode data = successfulData(response, "Unable to initialize Paystack payment");
        return new InitializeResult(requiredText(data, "reference"), requiredText(data, "authorization_url"));
    }

    public Verification verify(String reference) {
        JsonNode response = send(URI.create(VERIFY_URI + java.net.URLEncoder.encode(reference,
                java.nio.charset.StandardCharsets.UTF_8)), "GET", null);
        JsonNode data = successfulData(response, "Unable to verify Paystack payment");
        if (!"success".equalsIgnoreCase(requiredText(data, "status"))) {
            throw new IllegalStateException("Paystack payment was not successful");
        }
        if (!data.path("amount").canConvertToLong()) {
            throw new IllegalStateException("Paystack verification response was invalid");
        }
        Long processorFeeMinor = data.path("fees").canConvertToLong() ? data.path("fees").longValue() : null;
        return new Verification(requiredText(data, "reference"), data.path("amount").longValue(),
                requiredText(data, "currency"), requiredText(data, "id"), processorFeeMinor);
    }

    public String createSubaccount(SubaccountRequest request) {
        return subaccountCode(successfulData(send(SUBACCOUNT_URI, "POST", write(request)),
                "Unable to create Paystack payout destination"));
    }

    public String updateSubaccount(String subaccountCode, SubaccountRequest request) {
        if (subaccountCode == null || subaccountCode.isBlank()) {
            throw new IllegalArgumentException("Paystack subaccount is required");
        }
        return subaccountCode(successfulData(send(URI.create(SUBACCOUNT_UPDATE_URI
                + java.net.URLEncoder.encode(subaccountCode, java.nio.charset.StandardCharsets.UTF_8)), "PUT", write(request)),
                "Unable to update Paystack payout destination"));
    }

    private JsonNode send(URI uri, String method, String body) {
        requireSecret();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Accept", "application/json");
            if ("POST".equals(method) || "PUT".equals(method)) {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                request.GET();
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Paystack request was rejected");
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to reach Paystack", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to reach Paystack", exception);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Paystack response was invalid", exception);
        }
    }

    private String write(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to initialize Paystack payment", exception);
        }
    }

    private JsonNode successfulData(JsonNode response, String message) {
        if (!response.path("status").asBoolean(false) || response.path("data").isMissingNode()
                || response.path("data").isNull()) {
            throw new IllegalStateException(message);
        }
        return response.path("data");
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Paystack response was invalid");
        }
        return value;
    }

    private void requireSecret() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack integration is not configured");
        }
    }

    private String subaccountCode(JsonNode data) {
        return requiredText(data, "subaccount_code");
    }

    public record InitializeRequest(String email, String amount, String currency, String reference, String callback_url,
            String metadata, String subaccount, Long transaction_charge, String bearer) {
    }

    public record InitializeResult(String reference, String authorizationUrl) {
    }

    public record Verification(String reference, long amountMinor, String currency, String transactionId,
            Long processorFeeMinor) {
    }

    public record SubaccountRequest(String business_name, String bank_code, String account_number, String description) {
    }
}
