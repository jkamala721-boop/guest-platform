package com.guest_platform.service.notification;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;
import com.guest_platform.entity.NotificationType;

/** Sends only approved Meta templates; it never assumes free-form business messaging is permitted. */
@Component
public class WhatsAppNotificationProvider implements NotificationProvider {

    private final WhatsAppTransport transport;
    private final String accessToken;
    private final String phoneNumberId;
    private final String apiVersion;
    private final String manualTemplateName;
    private final String guestLinkTemplateName;
    private final String scheduledTemplateName;
    private final String languageCode;

    public WhatsAppNotificationProvider(ObjectProvider<WhatsAppTransport> transportProvider,
            @Value("${app.notifications.whatsapp.access-token:}") String accessToken,
            @Value("${app.notifications.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${app.notifications.whatsapp.api-version:}") String apiVersion,
            @Value("${app.notifications.whatsapp.manual-template-name:}") String manualTemplateName,
            @Value("${app.notifications.whatsapp.guest-link-template-name:}") String guestLinkTemplateName,
            @Value("${app.notifications.whatsapp.scheduled-template-name:}") String scheduledTemplateName,
            @Value("${app.notifications.whatsapp.language-code:en}") String languageCode) {
        this.transport = transportProvider.getIfAvailable();
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
        this.apiVersion = apiVersion;
        this.manualTemplateName = manualTemplateName;
        this.guestLinkTemplateName = guestLinkTemplateName;
        this.scheduledTemplateName = scheduledTemplateName;
        this.languageCode = languageCode;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public String readinessError(Notification notification) {
        if (transport == null || blank(accessToken) || blank(phoneNumberId) || blank(apiVersion)) {
            return "WhatsApp delivery is not configured";
        }
        try {
            WhatsAppPhoneNumbers.normalize(notification.getGuest());
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
        if (blank(templateName(notification))) {
            return "An approved WhatsApp template is required for this notification";
        }
        if (notification.getType() == NotificationType.GUEST_LINK && notification.getDeliveryParameters().size() != 5) {
            return "Secure guest link delivery data is unavailable";
        }
        return null;
    }

    @Override
    public void deliver(Notification notification) {
        String readinessError = readinessError(notification);
        if (readinessError != null) {
            throw new IllegalStateException(readinessError);
        }
        transport.send(new WhatsAppTemplateMessage(WhatsAppPhoneNumbers.normalize(notification.getGuest()),
                templateName(notification), languageCode, parameters(notification)));
    }

    private String templateName(Notification notification) {
        return switch (notification.getType()) {
            case MANUAL_MESSAGE -> manualTemplateName;
            case GUEST_LINK -> guestLinkTemplateName;
            default -> scheduledTemplateName;
        };
    }

    private List<String> parameters(Notification notification) {
        return switch (notification.getType()) {
            case MANUAL_MESSAGE -> List.of(notification.getMessage());
            case GUEST_LINK -> notification.getDeliveryParameters();
            default -> List.of(notification.getGuest().getFullName(), notification.getBooking().getProperty().getName(),
                    notification.getBooking().getCheckInDate().toString(), notification.getBooking().getCheckOutDate().toString());
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
