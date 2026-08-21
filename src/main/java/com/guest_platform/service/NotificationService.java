package com.guest_platform.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.NotificationResponse;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;
import com.guest_platform.entity.NotificationStatus;
import com.guest_platform.entity.NotificationType;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.NotificationRepository;
import com.guest_platform.service.notification.NotificationProvider;

@Service
public class NotificationService {

    private static final EnumSet<BookingStatus> UPCOMING_STATUSES = EnumSet.of(
            BookingStatus.PENDING_CONFIRMATION, BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);
    private static final EnumSet<BookingStatus> CHECKOUT_STATUSES = EnumSet.of(
            BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final AvailabilityService availabilityService;
    private final Map<NotificationChannel, NotificationProvider> providers;
    private final long paymentReminderHoursBeforeCheckIn;

    public NotificationService(BookingRepository bookingRepository, NotificationRepository notificationRepository,
            AvailabilityService availabilityService, List<NotificationProvider> notificationProviders,
            @Value("${app.notifications.payment-reminder-hours-before-checkin:12}")
            long paymentReminderHoursBeforeCheckIn) {
        if (paymentReminderHoursBeforeCheckIn < 1 || paymentReminderHoursBeforeCheckIn > 47) {
            throw new IllegalArgumentException("payment reminder hours must be between 1 and 47");
        }
        this.bookingRepository = bookingRepository;
        this.notificationRepository = notificationRepository;
        this.availabilityService = availabilityService;
        this.providers = notificationProviders.stream().collect(Collectors.toMap(NotificationProvider::channel,
                Function.identity(), (first, ignored) -> first, () -> new EnumMap<>(NotificationChannel.class)));
        this.paymentReminderHoursBeforeCheckIn = paymentReminderHoursBeforeCheckIn;
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
        Instant scheduledAt = triggerAt.isAfter(now) ? triggerAt : now;
        if (notification == null) {
            notificationRepository.save(new Notification(booking, type, NotificationChannel.MOCK, scheduledAt));
        } else {
            notification.refreshRecipient(booking.getGuest());
            notification.reschedule(scheduledAt);
        }
    }

    private void deliverDueNotification(UUID notificationId, Instant now) {
        Notification notification = notificationRepository.findForUpdateById(notificationId).orElse(null);
        if (notification == null || !notification.isDueAt(now)) {
            return;
        }
        if (!isRelevantAtDelivery(notification, now)) {
            notification.cancel();
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
            notification.markFailed("Delivery failed");
        }
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
            default -> "Mock delivery completed";
        };
    }
}
