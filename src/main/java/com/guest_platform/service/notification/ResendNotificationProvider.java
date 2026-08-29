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
@ConditionalOnProperty(name = "app.notifications.resend.enabled", havingValue = "true", matchIfMissing = true)
public class ResendNotificationProvider implements NotificationProvider {

    private final Resend resend;
    private final String apiKey;
    private final String fromAddress;

    public ResendNotificationProvider(
            @Value("${app.notifications.resend.api-key}") String apiKey,
            @Value("${app.notifications.resend.from:onboarding@resend.dev}") String fromAddress) {

        this.apiKey = apiKey;
        this.resend = apiKey == null || apiKey.isBlank() ? null : new Resend(apiKey);
        this.fromAddress = fromAddress;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public String readinessError(Notification notification) {
        if (apiKeyMissing()) {
            return "Email delivery is not configured";
        }
        if (notification.getGuest().getEmail() == null || notification.getGuest().getEmail().isBlank()) {
            return "A guest email is required before sending an email notification";
        }
        return null;
    }

    @Override
    public void deliver(Notification notification) {
        String readinessError = readinessError(notification);
        if (readinessError != null) {
            throw new IllegalStateException(readinessError);
        }
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
            case GUEST_LINK -> "Your Hostvero stay link";
            case EMAIL_VERIFICATION -> "Verify your Hostvero email";
            case RETURNING_GUEST_VERIFICATION -> "Verify your previous Hostvero stay";
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

            case CHECKOUT_REMINDER -> {
                String customMessage = notification.getBooking().getCheckoutReminderMessage();

                String message = customMessage == null || customMessage.isBlank()
                        ? "This is a reminder that your Hostvero checkout is approaching. Please review your stay information before leaving the property."
                        : customMessage;

                 yield """
                          <p>Hello %s,</p>
                          <p>%s</p>
                          <p>Hostvero</p>
                          """.formatted(
                                  guestName,
                                  escapeHtml(message).replace("\n", "<br>")
                          );
            }
            case MANUAL_MESSAGE -> """
                    <p>Hello %s,</p>
                    <p>%s</p>
                    <p>Hostvero</p>
                    """.formatted(guestName, escapeHtml(notification.getMessage()).replace("\n", "<br>"));
            case GUEST_LINK -> """
                    <p>Hello %s,</p>
                    <p>Your stay at %s is from %s to %s.</p>
                    <p>Your secure Hostvero guest link:</p>
                    <p><a href="%s">Open your guest link</a></p>
                    <p>Please do not share this link publicly.</p>
                    <p>Hostvero</p>
                    """.formatted(guestName, escapeHtml(parameter(notification, 1)), escapeHtml(parameter(notification, 2)),
                            escapeHtml(parameter(notification, 3)), escapeHtml(parameter(notification, 4)));
            case EMAIL_VERIFICATION -> """
                    <p>Your Hostvero email verification code is:</p>
                    <p><strong>%s</strong></p>
                    <p>This code expires in %s minutes. If you did not request it, you can ignore this email.</p>
                    """.formatted(escapeHtml(parameter(notification, 0)), escapeHtml(parameter(notification, 1)));
            case RETURNING_GUEST_VERIFICATION -> """
                    <p>Your Hostvero verification code is:</p><p><strong>%s</strong></p>
                    <p>This code expires in %s minutes. If you did not request it, you can ignore this email.</p>
                    """.formatted(escapeHtml(parameter(notification, 0)), escapeHtml(parameter(notification, 1)));
        };
    }

    private boolean apiKeyMissing() {
        return apiKey == null || apiKey.isBlank();
    }

    private String parameter(Notification notification, int index) {
        if (notification.getDeliveryParameters().size() <= index) {
            throw new IllegalStateException("Secure guest link delivery data is unavailable");
        }
        return notification.getDeliveryParameters().get(index);
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
