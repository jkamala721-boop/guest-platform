package com.guest_platform.service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.core.exception.ResendException;

@Component
@ConditionalOnProperty(
        name = "app.notifications.mode",
        havingValue = "email"
)
public class ResendNotificationProvider implements NotificationProvider {

    private final Resend resend;
    private final String fromAddress;

    public ResendNotificationProvider(
            @Value("${app.notifications.resend.api-key}") String apiKey,
            @Value("${app.notifications.resend.from:onboarding@resend.dev}") String fromAddress) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Resend API key is not configured");
        }

        this.resend = new Resend(apiKey);
        this.fromAddress = fromAddress;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void deliver(Notification notification) {
        String recipient = notification.getGuest().getEmail();

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("Guest email is not available");
        }

        CreateEmailOptions request = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(recipient)
                .subject(subject(notification))
                .html(body(notification))
                .build();

        try {
            resend.emails().send(request);
        } catch (ResendException exception) {
            throw new IllegalStateException("Resend delivery failed: " + failureCategory(exception), exception);
        }
    }

    private String failureCategory(ResendException exception) {
        String detail = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (detail.contains("api key") || detail.contains("authentication") || detail.contains("unauthorized")) {
            return "authentication rejected";
        }
        if (detail.contains("sender") || detail.contains("from address") || detail.contains("domain")) {
            return "sender rejected";
        }
        if (detail.contains("recipient") || detail.contains("to address") || detail.contains("invalid email")) {
            return "recipient rejected";
        }
        if (hasNetworkCause(exception)) {
            return "network request failed";
        }
        return "provider request failed";
    }

    private boolean hasNetworkCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.io.IOException) {
                return true;
            }
        }
        return false;
    }

    private String subject(Notification notification) {
        return switch (notification.getType()) {
            case TWO_DAY_REMINDER ->
                    "Your Hostvero stay is coming up";

            case TWENTY_FOUR_HOUR_PAYMENT_REQUEST ->
                    "Payment required for your Hostvero stay";

            case PAYMENT_REMINDER ->
                    "Reminder: complete your Hostvero payment";

            case CHECKOUT_REMINDER ->
                    "Hostvero checkout reminder";
            case MANUAL_MESSAGE -> notification.getSubject();
        };
    }

    private String body(Notification notification) {
        String guestName = escapeHtml(notification.getGuest().getFullName());

        return switch (notification.getType()) {
            case TWO_DAY_REMINDER -> """
                    <p>Hello %s,</p>
                    <p>Your Hostvero stay is coming up in two days.</p>
                    <p>Please keep your booking information handy and contact your host if you need any assistance.</p>
                    <p>Hostvero</p>
                    """.formatted(guestName);

            case TWENTY_FOUR_HOUR_PAYMENT_REQUEST -> """
                    <p>Hello %s,</p>
                    <p>Your Hostvero booking is approaching and payment is still required.</p>
                    <p>Please use your secure Hostvero guest link to complete payment.</p>
                    <p>Hostvero</p>
                    """.formatted(guestName);

            case PAYMENT_REMINDER -> """
                    <p>Hello %s,</p>
                    <p>This is a reminder that payment for your Hostvero stay is still pending.</p>
                    <p>Please complete payment using your secure Hostvero guest link.</p>
                    <p>Hostvero</p>
                    """.formatted(guestName);

            case CHECKOUT_REMINDER -> """
                    <p>Hello %s,</p>
                    <p>This is a reminder that your Hostvero checkout is approaching.</p>
                    <p>Please review your stay information before leaving the property.</p>
                    <p>Hostvero</p>
                    """.formatted(guestName);

            case MANUAL_MESSAGE -> """
                    <p>Hello %s,</p>
                    <p>%s</p>
                    <p>Hostvero</p>
                    """.formatted(guestName, escapeHtml(notification.getMessage()).replace("\n", "<br>"));
        };
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "Guest";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
