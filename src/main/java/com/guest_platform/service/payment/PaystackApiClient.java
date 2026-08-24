package com.guest_platform.service.payment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

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
    private static final URI TRANSFER_RECIPIENT_URI = URI.create("https://api.paystack.co/transferrecipient");
    private static final URI TRANSFER_URI = URI.create("https://api.paystack.co/transfer");
    private static final URI BALANCE_URI = URI.create("https://api.paystack.co/balance");
    private static final URI BANKS_URI = URI.create("https://api.paystack.co/bank?country=kenya");
    private static final String SUBACCOUNT_UPDATE_URI = "https://api.paystack.co/subaccount/";
    private static final String VERIFY_URI = "https://api.paystack.co/transaction/verify/";
    private static final String TRANSFER_VERIFY_URI = "https://api.paystack.co/transfer/verify/";

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

    public String createTransferRecipient(TransferRecipientRequest request) {
        return recipientCode(successfulData(send(TRANSFER_RECIPIENT_URI, "POST", write(request)),
                "Unable to create Paystack M-Pesa payout destination"));
    }

    /** Initiates a host payout. A queued response is intentionally not treated as a completed transfer. */
    public TransferResult initiateTransfer(TransferRequest request) {
        JsonNode data = successfulData(send(TRANSFER_URI, "POST", write(request)),
                "Unable to initiate Paystack host payout");
        return new TransferResult(requiredText(data, "reference"), requiredText(data, "transfer_code"),
                requiredText(data, "status"));
    }

    /** Verifies an already-submitted transfer by its durable Hostvero reference. */
    public TransferResult verifyTransfer(String reference) {
        JsonNode data = successfulData(send(URI.create(TRANSFER_VERIFY_URI + java.net.URLEncoder.encode(reference,
                java.nio.charset.StandardCharsets.UTF_8)), "GET", null), "Unable to verify Paystack host payout");
        return new TransferResult(requiredText(data, "reference"), optionalText(data, "transfer_code"),
                requiredText(data, "status"));
    }

    /** Paystack exposes only an integration-level balance, so it is a final availability gate after the settlement hold. */
    public boolean hasAvailableBalance(String currency, long requiredAmountMinor) {
        JsonNode data = successfulData(send(BALANCE_URI, "GET", null), "Unable to check Paystack balance");
        if (!data.isArray()) {
            throw new IllegalStateException("Paystack balance response was invalid");
        }
        for (JsonNode balance : data) {
            if (currency.equalsIgnoreCase(optionalText(balance, "currency"))
                    && balance.path("balance").canConvertToLong()
                    && balance.path("balance").longValue() >= requiredAmountMinor) {
                return true;
            }
        }
        return false;
    }

    public List<Bank> listKenyanBanks() {
        JsonNode data = successfulData(send(BANKS_URI, "GET", null), "Unable to list Paystack banks");
        if (!data.isArray()) {
            throw new IllegalStateException("Paystack bank response was invalid");
        }
        java.util.ArrayList<Bank> banks = new java.util.ArrayList<>();
        for (JsonNode bank : data) {
            String code = requiredText(bank, "code");
            String name = requiredText(bank, "name");
            if (bank.path("active").asBoolean(true)) {
                banks.add(new Bank(code, name));
            }
        }
        return banks;
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
                throw new PaystackRequestRejectedException(response.statusCode(),
                        safeProviderMessage(response.body()));
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

    private String optionalText(JsonNode node, String field) {
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private void requireSecret() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack integration is not configured");
        }
    }

    private String subaccountCode(JsonNode data) {
        return requiredText(data, "subaccount_code");
    }

    private String recipientCode(JsonNode data) {
        return requiredText(data, "recipient_code");
    }

    static String safeProviderMessage(String body) {
        try {
            JsonNode response = new ObjectMapper().readTree(body);
            String message = response.path("message").asText("").trim();
            if (!message.isBlank()) {
                return redactSensitiveNumbers(message.length() > 180 ? message.substring(0, 180) : message);
            }
        } catch (JacksonException ignored) {
            // Provider did not return structured JSON; never surface its raw body.
        }
        return "Paystack rejected the request";
    }

    private static String redactSensitiveNumbers(String value) {
        return value.replaceAll("(?<!\\d)(?:\\+?254|0)7\\d{8}(?!\\d)", "[redacted]")
                .replaceAll("(?<!\\d)\\d{8,}(?!\\d)", "[redacted]");
    }

    public static final class PaystackRequestRejectedException extends RuntimeException {
        private final int statusCode;
        private final String providerMessage;

        public PaystackRequestRejectedException(int statusCode, String providerMessage) {
            super("Paystack request rejected: status=" + statusCode + ", message=" + providerMessage);
            this.statusCode = statusCode;
            this.providerMessage = providerMessage;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getProviderMessage() {
            return providerMessage;
        }
    }

    public record InitializeRequest(String email, String amount, String currency, String reference, String callback_url,
            String metadata, String subaccount, Long transaction_charge, String bearer) {
    }

    public record InitializeResult(String reference, String authorizationUrl) {
    }

    public record Verification(String reference, long amountMinor, String currency, String transactionId,
            Long processorFeeMinor) {
    }

    public record SubaccountRequest(String business_name, String bank_code, String account_number,
            java.math.BigDecimal percentage_charge, String description) {
    }

    public record TransferRecipientRequest(String type, String name, String account_number, String bank_code,
            String currency) {
    }

    public record TransferRequest(String source, long amount, String recipient, String reference, String reason,
            String currency) {
    }

    public record TransferResult(String reference, String transferCode, String status) {
    }

    public record Bank(String code, String name) {
    }
}
