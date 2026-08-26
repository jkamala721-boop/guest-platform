package com.guest_platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.Guest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostNotification;
import com.guest_platform.entity.HostNotificationType;
import com.guest_platform.entity.Property;
import com.guest_platform.repository.HostNotificationRepository;
import com.guest_platform.service.notification.ResendHostNotificationClient;

class HostNotificationDeliveryServiceTest {
    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void exactVariablesAndPublicBaseBookingUrlAreUsedWithoutSensitiveValues() {
        HostNotification notification = notification(HostNotificationType.PAYMENT_CONFIRMED);
        HostNotificationDeliveryService service = new HostNotificationDeliveryService(
                mock(HostNotificationRepository.class), mock(ResendHostNotificationClient.class),
                "https://app.hostvero.net/");

        Map<String, String> variables = service.variables(notification);

        assertThat(variables).containsOnlyKeys("HOST_NAME", "PROPERTY_NAME", "NOTIFICATION_TITLE", "MESSAGE",
                "ACTION_LABEL", "ACTION_URL", "FIRST_NAME");
        assertThat(variables).containsEntry("HOST_NAME", "Grace Host")
                .containsEntry("FIRST_NAME", "Grace")
                .containsEntry("PROPERTY_NAME", "Garden Suite")
                .containsEntry("NOTIFICATION_TITLE", "Payment confirmed")
                .containsEntry("ACTION_LABEL", "View booking")
                .containsEntry("ACTION_URL", "https://app.hostvero.net/#/bookings/" + BOOKING_ID);
        assertThat(variables.get("MESSAGE")).isEqualTo(
                "Amina Guest has completed payment for the booking at Garden Suite.")
                .doesNotContain("passport", "access", "token", "Paystack");
    }

    @Test
    void cancellationAndPayoutContentUseSafeHostActions() {
        HostNotificationDeliveryService service = new HostNotificationDeliveryService(
                mock(HostNotificationRepository.class), mock(ResendHostNotificationClient.class),
                "https://app.hostvero.net");
        Map<String, String> cancelled = service.variables(notification(HostNotificationType.BOOKING_CANCELLED));
        Map<String, String> payout = service.variables(notification(HostNotificationType.PAYOUT_ISSUE));

        assertThat(cancelled.get("MESSAGE")).contains("Amina Guest", "Garden Suite", "2030-01-10", "2030-01-12");
        assertThat(payout).containsEntry("NOTIFICATION_TITLE", "Payout needs attention")
                .containsEntry("ACTION_LABEL", "Review payout settings")
                .containsEntry("ACTION_URL", "https://app.hostvero.net/#/settings");
        assertThat(payout.get("MESSAGE")).doesNotContain("response", "code", "reference");
    }

    private HostNotification notification(HostNotificationType type) {
        Host host = mock(Host.class);
        Guest guest = mock(Guest.class);
        Property property = mock(Property.class);
        Booking booking = mock(Booking.class);
        HostNotification notification = mock(HostNotification.class);
        when(host.getFullName()).thenReturn("Grace <Host>");
        when(guest.getFullName()).thenReturn("Amina <Guest>");
        when(property.getName()).thenReturn("Garden <Suite>");
        when(booking.getId()).thenReturn(BOOKING_ID);
        when(booking.getGuest()).thenReturn(guest);
        when(booking.getProperty()).thenReturn(property);
        when(booking.getCheckInDate()).thenReturn(LocalDate.of(2030, 1, 10));
        when(booking.getCheckOutDate()).thenReturn(LocalDate.of(2030, 1, 12));
        when(notification.getHost()).thenReturn(host);
        when(notification.getBooking()).thenReturn(booking);
        when(notification.getType()).thenReturn(type);
        return notification;
    }
}

