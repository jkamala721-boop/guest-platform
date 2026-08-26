package com.guest_platform.service.notification;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ResendHostNotificationClient {
    static final String EMAIL_ENDPOINT = "/emails";

    private final RestClient restClient;
    private final String apiKey;
    private final String fromAddress;
    private final String templateId;

    public ResendHostNotificationClient(RestClient.Builder builder,
            @Value("${app.notifications.resend.api-key:}") String apiKey,
            @Value("${app.notifications.resend.from:onboarding@resend.dev}") String fromAddress,
            @Value("${app.notifications.resend.host-template-id:}") String templateId) {
        this.restClient = builder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.templateId = templateId;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && fromAddress != null && !fromAddress.isBlank()
                && templateId != null && !templateId.isBlank();
    }

    public void send(String recipient, Map<String, String> variables) {
        if (!isConfigured()) {
            throw new HostNotificationDeliveryException("Resend host template delivery is not configured");
        }
        Map<String, Object> payload = Map.of(
                "from", fromAddress,
                "to", List.of(recipient),
                "template", Map.of("id", templateId, "variables", variables));
        try {
            restClient.post().uri(EMAIL_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new HostNotificationDeliveryException(
                    "Resend host template delivery failed with HTTP " + exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            throw new HostNotificationDeliveryException("Resend host template delivery failed");
        }
    }

    public String templateId() { return templateId; }

    public static final class HostNotificationDeliveryException extends RuntimeException {
        public HostNotificationDeliveryException(String message) { super(message); }
    }
}

