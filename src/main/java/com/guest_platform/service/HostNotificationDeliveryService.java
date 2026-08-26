package com.guest_platform.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostNotification;
import com.guest_platform.entity.HostNotificationStatus;
import com.guest_platform.repository.HostNotificationRepository;
import com.guest_platform.service.notification.ResendHostNotificationClient;

@Service
public class HostNotificationDeliveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HostNotificationDeliveryService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final HostNotificationRepository repository;
    private final ResendHostNotificationClient resend;
    private final String publicBaseUrl;

    public HostNotificationDeliveryService(HostNotificationRepository repository,
            ResendHostNotificationClient resend,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.repository = repository;
        this.resend = resend;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID notificationId) {
        HostNotification notification = repository.findForUpdateById(notificationId).orElse(null);
        if (notification == null || notification.getStatus() != HostNotificationStatus.PENDING) return;
        Host host = notification.getHost();
        String recipient = host.getEmail();
        if (!validEmail(recipient)) {
            LOGGER.warn("Host notification skipped because host email is unavailable: notificationId={}", notificationId);
            notification.markSkipped("Host email is unavailable");
            return;
        }
        if (!resend.isConfigured()) {
            LOGGER.warn("Host notification failed because Resend host template delivery is not configured: notificationId={}",
                    notificationId);
            notification.markFailed();
            return;
        }
        try {
            resend.send(recipient, variables(notification));
            notification.markSent(Instant.now());
        } catch (RuntimeException exception) {
            LOGGER.warn("Host notification delivery failed: notificationId={}, exceptionClass={}", notificationId,
                    exception.getClass().getSimpleName());
            notification.markFailed();
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> pendingIds() {
        return repository.findTop50ByStatusOrderByCreatedAtAsc(HostNotificationStatus.PENDING).stream()
                .map(HostNotification::getId).toList();
    }

    Map<String, String> variables(HostNotification notification) {
        Booking booking = notification.getBooking();
        String hostName = safeText(notification.getHost().getFullName(), "Host");
        String propertyName = safeText(booking.getProperty().getName(), "Your property");
        String guestName = booking.getGuest() == null ? "A guest" : safeText(booking.getGuest().getFullName(), "A guest");
        String title;
        String message;
        String actionLabel;
        String actionUrl;
        switch (notification.getType()) {
            case PAYMENT_CONFIRMED -> {
                title = "Payment confirmed";
                message = guestName + " has completed payment for the booking at " + propertyName + ".";
                actionLabel = "View booking";
                actionUrl = bookingUrl(booking);
            }
            case BOOKING_CANCELLED -> {
                title = "Booking cancelled";
                message = guestName + "'s booking at " + propertyName + " from "
                        + DateTimeFormatter.ISO_LOCAL_DATE.format(booking.getCheckInDate()) + " to "
                        + DateTimeFormatter.ISO_LOCAL_DATE.format(booking.getCheckOutDate()) + " was cancelled.";
                actionLabel = "View booking";
                actionUrl = bookingUrl(booking);
            }
            case PAYOUT_ISSUE -> {
                title = "Payout needs attention";
                message = "We couldn't complete a payout for one of your bookings. Please review your payout settings.";
                actionLabel = "Review payout settings";
                actionUrl = publicBaseUrl + "/#/settings";
            }
            default -> throw new IllegalStateException("Unsupported host notification type");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("HOST_NAME", hostName);
        values.put("PROPERTY_NAME", propertyName);
        values.put("NOTIFICATION_TITLE", title);
        values.put("MESSAGE", safeText(message, "Hostvero notification"));
        values.put("ACTION_LABEL", actionLabel);
        values.put("ACTION_URL", actionUrl);
        values.put("FIRST_NAME", firstName(hostName));
        return Map.copyOf(values);
    }

    private String bookingUrl(Booking booking) { return publicBaseUrl + "/#/bookings/" + booking.getId(); }

    private boolean validEmail(String value) {
        return value != null && value.length() <= 320 && EMAIL.matcher(value.trim()).matches();
    }

    private String firstName(String fullName) {
        String value = safeText(fullName, "Host");
        int separator = value.indexOf(' ');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String safe = value.replaceAll("[<>]", "").replaceAll("[\\r\\n\\t]+", " ").trim();
        if (safe.isBlank()) return fallback;
        return safe.substring(0, Math.min(safe.length(), 500));
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Hostvero public base URL is required");
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
