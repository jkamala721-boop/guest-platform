package com.guest_platform.service.notification;

public interface WhatsAppTransport {
    void send(WhatsAppTemplateMessage message);
}
