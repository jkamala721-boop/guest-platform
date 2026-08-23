package com.guest_platform.service.notification;

import java.util.List;

/** Sanitized, provider-neutral payload for an approved WhatsApp template. */
public record WhatsAppTemplateMessage(String recipient, String templateName, String languageCode,
        List<String> bodyParameters) {

    public WhatsAppTemplateMessage {
        bodyParameters = List.copyOf(bodyParameters);
    }
}
