package com.guest_platform.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.NotificationResponse;
import com.guest_platform.dto.ManualNotificationRequest;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;
import com.guest_platform.entity.NotificationStatus;
import com.guest_platform.entity.NotificationType;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.NotificationRepository;
import com.guest_platform.service.notification.NotificationProvider;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private static final EnumSet<BookingStatus> UPCOMING_STATUSES = EnumSet.of(
            BookingStatus.PENDING_CONFIRMATION, BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);
    private static final EnumSet<BookingStatus> CHECKOUT_STATUSES = EnumSet.of(
            BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final AvailabilityService availabilityService;
    private final Map<NotificationChannel, NotificationProvider> providers;
    private final NotificationChannel notificationChannel;
    private final long paymentReminderHoursBeforeCheckIn;


    public NotificationService(
        BookingRepository bookingRepository,
        NotificationRepository notificationRepository,
        AvailabilityService availabilityService,
        List<NotificationProvider> notificationProviders,
        @Value("${app.notifications.default-channel:${app.notifications.mode:mock}}") String defaultChannel,
        @Value("${app.notifications.payment-reminder-hours-before-checkin:12}")
        long paymentReminderHoursBeforeCheckIn) {

    if (paymentReminderHoursBeforeCheckIn < 1 || paymentReminderHoursBeforeCheckIn > 47) {
        throw new IllegalArgumentException("payment reminder hours must be between 1 and 47");
    }

    this.bookingRepository = bookingRepository;
    this.notificationRepository = notificationRepository;
    this.availabilityService = availabilityService;

    this.providers = notificationProviders.stream()
            .collect(Collectors.toMap(
                    NotificationProvider::channel,
                    Function.identity(),
                    (first, ignored) -> first,
                    () -> new EnumMap<>(NotificationChannel.class)));

    try {
        this.notificationChannel = NotificationChannel.valueOf(defaultChannel.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("Notification default channel is invalid");
    }

    this.paymentReminderHoursBeforeCheckIn = paymentReminderHoursBeforeCheckIn;
}

    @Transactional
    public NotificationResponse sendManual(UUID hostId, UUID bookingId, ManualNotificationRequest request) {
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        return send(booking, NotificationType.MANUAL_MESSAGE, request.channel(), request.subject().trim(),
                request.message().trim(), List.of());
    }

    @Transactional
    public NotificationResponse sendGuestLink(UUID hostId, UUID bookingId, NotificationChannel channel,
            List<String> deliveryParameters) {
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        return send(booking, NotificationType.GUEST_LINK, channel, "Your Hostvero stay link",
                "Secure guest link delivery", deliveryParameters);
    }

    @Transactional
    public NotificationResponse sendEmailVerification(Booking booking, String code, long codeTtlSeconds) {
        return send(booking, NotificationType.EMAIL_VERIFICATION, NotificationChannel.EMAIL, "Verify your Hostvero email",
                "Email verification code delivery", List.of(code, Long.toString(codeTtlSeconds / 60)));
    }

    private NotificationResponse send(Booking booking, NotificationType type, NotificationChannel channel, String subject,
            String message, List<String> deliveryParameters) {
        if (booking.getGuest() == null) {
            throw new ConflictException("A guest is required before sending a notification");
        }
        if (channel == NotificationChannel.EMAIL && type != NotificationType.EMAIL_VERIFICATION
                && !booking.getGuest().isEmailVerified()) {
            throw new ConflictException("The guest email must be verified before sending email notifications");
        }
        NotificationProvider provider = providers.get(channel);
        if (provider == null) {
            throw new ConflictException("That notification channel is not configured");
        }
        Notification notification = new Notification(booking, type, channel, subject, message, Instant.now());
        notification.setDeliveryParameters(deliveryParameters);
        String readinessError = provider.readinessError(notification);
        if (readinessError != null) {
            throw new ConflictException(readinessError);
        }
        notification = notificationRepository.save(notification);
        deliverDueNotification(notification.getId(), Instant.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void reconcileBooking(UUID bookingId) {
        reconcileBooking(bookingId, Instant.now());
    }

    /** Visible for deterministic scheduler tests; production calls use Instant.now(). */
    @Transactional
    public void reconcileBooking(UUID bookingId, Instant now) {
        bookingRepository.findForUpdateById(bookingId).ifPresent(booking -> synchronize(booking, now));
    }

    @Transactional
    public void reconcileAll() {
        reconcileAll(Instant.now());
    }

    @Transactional
    public void reconcileAll(Instant now) {
        bookingRepository.findAll().forEach(booking -> bookingRepository.findForUpdateById(booking.getId())
                .ifPresent(lockedBooking -> synchronize(lockedBooking, now)));
    }

    @Transactional
    public void cancelPendingForBooking(UUID bookingId) {
        notificationRepository.findAllByBookingId(bookingId).forEach(Notification::cancel);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID hostId) {
        return notificationRepository.findAllByHostIdOrderByScheduledAtDesc(hostId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForBooking(UUID hostId, UUID bookingId) {
        bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        return notificationRepository.findAllByBookingIdAndHostIdOrderByScheduledAtDesc(bookingId, hostId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(UUID hostId, UUID notificationId) {
        return NotificationResponse.from(notificationRepository.findByIdAndHostId(notificationId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification was not found")));
    }

    @Transactional
    public void reconcileAndDeliver() {
        Instant now = Instant.now();
        reconcileAll(now);
        deliverDueNotifications(now);
    }

    @Transactional
    public void deliverDueNotifications(Instant now) {
        notificationRepository.findAllByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                NotificationStatus.PENDING, now).stream()
                .map(Notification::getId)
                .forEach(notificationId -> deliverDueNotification(notificationId, now));
    }

    private void synchronize(Booking booking, Instant now) {
        // A booking is valid before its guest completes the secure-link
        // registration. Guest notifications cannot be delivered without a
        // recipient, so defer scheduling until registration attaches one.
        if (booking.getGuest() == null) {
            notificationRepository.findAllByBookingId(booking.getId()).forEach(Notification::cancel);
            return;
        }

        Instant checkInAt = booking.getCheckInDate().atTime(booking.getProperty().getCheckInTime())
                .toInstant(ZoneOffset.UTC);
        Instant checkOutAt = booking.getCheckOutDate().atTime(booking.getProperty().getCheckOutTime())
                .toInstant(ZoneOffset.UTC);

        sync(booking, NotificationType.TWO_DAY_REMINDER, checkInAt.minusSeconds(48 * 60 * 60),
                isUpcoming(booking, checkInAt, now), now);
        sync(booking, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST, checkInAt.minusSeconds(24 * 60 * 60),
                requiresPayment(booking, checkInAt, now), now);
        sync(booking, NotificationType.PAYMENT_REMINDER,
                checkInAt.minusSeconds(paymentReminderHoursBeforeCheckIn * 60 * 60),
                requiresPayment(booking, checkInAt, now), now);
        sync(booking, NotificationType.CHECKOUT_REMINDER, checkOutAt.minusSeconds(60 * 60),
                isCheckoutCandidate(booking, checkOutAt, now), now);
    }

    private void sync(Booking booking, NotificationType type, Instant triggerAt, boolean shouldExist, Instant now) {
        Notification notification = notificationRepository.findByBookingIdAndType(booking.getId(), type).orElse(null);
        if (!shouldExist) {
            if (notification != null) {
                notification.cancel();
            }
            return;
        }
        if (notification == null) {
            if (triggerAt.isAfter(now)) {
                notificationRepository.save(new Notification(booking, type, notificationChannel, triggerAt));
            }
            return;
        }

        // A notification already scheduled for this exact trigger remains a legitimate
        // pending delivery when the scheduler reaches its due time.  By contrast, a
        // booking-date change whose new trigger is already past invalidates a future
        // pending schedule rather than converting it into an immediate stale reminder.
        if (!triggerAt.isAfter(now)) {
            if (!notification.getScheduledAt().equals(triggerAt)) {
                notification.cancel();
            }
            return;
        }

        notification.refreshRecipient(booking.getGuest());
        notification.reschedule(triggerAt);
    }

    private void deliverDueNotification(UUID notificationId, Instant now) {
        Notification notification = notificationRepository.findForUpdateById(notificationId).orElse(null);
        if (notification == null || !notification.isDueAt(now)) {
            return;
        }
        if (notification.getGuest() == null) {
            notification.cancel();
            return;
        }
        if (!isRelevantAtDelivery(notification, now)) {
            notification.cancel();
            return;
        }
        if (notification.getChannel() == NotificationChannel.EMAIL
                && notification.getType() != NotificationType.EMAIL_VERIFICATION
                && !notification.getGuest().isEmailVerified()) {
            notification.markFailed("Guest email is not verified");
            return;
        }

        Boolean extensionAvailable = notification.getType() == NotificationType.CHECKOUT_REMINDER
                ? availabilityService.isAvailableForExtension(notification.getBooking().getProperty().getId(),
                        notification.getBooking().getCheckOutDate(), notification.getBooking().getId())
                : null;
        NotificationProvider provider = providers.get(notification.getChannel());
        if (provider == null) {
            notification.markFailed("No delivery provider is configured for this channel");
            return;
        }
        try {
            provider.deliver(notification);
            notification.markSent(now, extensionAvailable, deliveryDetail(notification.getType()));
        } catch (RuntimeException exception) {
            // Do not retain arbitrary provider exception text because it can contain sensitive data.
            LOGGER.warn("Notification delivery failed: notificationId={}, channel={}, exceptionClass={}, message={}",
                    notification.getId(), notification.getChannel(), exception.getClass().getSimpleName(),
                    safeExceptionMessage(exception));
            notification.markFailed("Delivery failed");
        }
    }

    private String safeExceptionMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        String safeMessage = message.replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer [redacted]")
                .replaceAll("(?i)(api[-_ ]?key|authorization|token|secret|password)\\s*[:=]\\s*[^\\s,;]+",
                        "$1=[redacted]")
                .replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[redacted-email]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return safeMessage.substring(0, Math.min(safeMessage.length(), 300));
    }

    private boolean isRelevantAtDelivery(Notification notification, Instant now) {
        Booking booking = notification.getBooking();
        Instant checkInAt = booking.getCheckInDate().atTime(booking.getProperty().getCheckInTime())
                .toInstant(ZoneOffset.UTC);
        Instant checkOutAt = booking.getCheckOutDate().atTime(booking.getProperty().getCheckOutTime())
                .toInstant(ZoneOffset.UTC);
        return switch (notification.getType()) {
            case TWO_DAY_REMINDER -> isUpcoming(booking, checkInAt, now);
            case TWENTY_FOUR_HOUR_PAYMENT_REQUEST, PAYMENT_REMINDER -> requiresPayment(booking, checkInAt, now);
            case CHECKOUT_REMINDER -> (booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.CHECKED_IN) && checkOutAt.isAfter(now);
            case MANUAL_MESSAGE, GUEST_LINK, EMAIL_VERIFICATION -> true;
        };
    }

    private boolean isUpcoming(Booking booking, Instant checkInAt, Instant now) {
        return UPCOMING_STATUSES.contains(booking.getStatus()) && checkInAt.isAfter(now);
    }

    private boolean requiresPayment(Booking booking, Instant checkInAt, Instant now) {
        return booking.getStatus() == BookingStatus.PENDING_PAYMENT && checkInAt.isAfter(now);
    }

    private boolean isCheckoutCandidate(Booking booking, Instant checkOutAt, Instant now) {
        return CHECKOUT_STATUSES.contains(booking.getStatus()) && checkOutAt.isAfter(now);
    }

    private String deliveryDetail(NotificationType type) {
        return switch (type) {
            case TWENTY_FOUR_HOUR_PAYMENT_REQUEST, PAYMENT_REMINDER ->
                    "Mock delivery completed; guest link action is required";
            case MANUAL_MESSAGE -> "Manual notification delivered";
            case GUEST_LINK -> "Guest link notification delivered";
            case EMAIL_VERIFICATION -> "Email verification code delivered";
            default -> "Mock delivery completed";
        };
    }
}
