package com.guest_platform.service.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Server-side transport for Meta's WhatsApp Cloud API. It never logs requests or response bodies. */
@Component
@ConditionalOnProperty(name = "app.notifications.whatsapp.enabled", havingValue = "true")
public class MetaWhatsAppCloudApiTransport implements WhatsAppTransport {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String accessToken;
    private final String phoneNumberId;
    private final String apiVersion;

    public MetaWhatsAppCloudApiTransport(ObjectMapper objectMapper,
            @Value("${app.notifications.whatsapp.access-token:}") String accessToken,
            @Value("${app.notifications.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${app.notifications.whatsapp.api-version:}") String apiVersion) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
        this.apiVersion = apiVersion;
    }

    @Override
    public void send(WhatsAppTemplateMessage message) {
        requireConfiguration();
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(message)))
                    .timeout(Duration.ofSeconds(20))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Meta WhatsApp delivery failed: " + failureCategory(response.statusCode()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Meta WhatsApp delivery failed: network request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta WhatsApp delivery failed: network request interrupted", exception);
        }
    }

    private URI endpoint() {
        return URI.create("https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages");
    }

    private String payload(WhatsAppTemplateMessage message) {
        List<Map<String, Object>> parameters = message.bodyParameters().stream()
                .map(value -> Map.<String, Object>of("type", "text", "text", value))
                .toList();
        Map<String, Object> template = Map.of(
                "name", message.templateName(),
                "language", Map.of("code", message.languageCode()),
                "components", List.of(Map.of("type", "body", "parameters", parameters)));
        try {
            return objectMapper.writeValueAsString(Map.of("messaging_product", "whatsapp", "to", message.recipient(),
                    "type", "template", "template", template));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Meta WhatsApp delivery failed: malformed request", exception);
        }
    }

    private void requireConfiguration() {
        if (blank(accessToken) || blank(phoneNumberId) || blank(apiVersion)) {
            throw new IllegalStateException("Meta WhatsApp delivery failed: provider is not configured");
        }
    }

    private String failureCategory(int status) {
        return switch (status) {
            case 400 -> "malformed request";
            case 401, 403 -> "authentication rejected";
            case 404 -> "provider configuration rejected";
            case 429 -> "rate limited";
            default -> status >= 500 ? "provider failure" : "provider request rejected";
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
