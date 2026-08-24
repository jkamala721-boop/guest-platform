package com.guest_platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;
import com.guest_platform.entity.NotificationStatus;
import com.guest_platform.entity.NotificationType;
import com.guest_platform.repository.NotificationRepository;
import com.guest_platform.service.NotificationService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseSixIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";
    private static final String MPESA_WEBHOOK_SECRET = "phase4-test-mpesa-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Test
    void schedulesAndDeliversTwoDayPaymentAndPaymentReminderExactlyOnce() throws Exception {
        String hostToken = register("phase6-schedule@example.com", "Schedule Host");
        String propertyId = createProperty(hostToken, "Schedule Property");
        String guestId = createGuest(hostToken, "Schedule Guest");
        LocalDate checkIn = LocalDate.now().plusDays(10);
        String bookingId = createBooking(hostToken, propertyId, guestId, checkIn, checkIn.plusDays(2), "PENDING_PAYMENT");

        Instant checkInAt = checkIn.atTime(14, 0).toInstant(ZoneOffset.UTC);
        Notification twoDay = notification(bookingId, NotificationType.TWO_DAY_REMINDER);
        Notification paymentRequest = notification(bookingId, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST);
        Notification paymentReminder = notification(bookingId, NotificationType.PAYMENT_REMINDER);
        assertEquals(NotificationChannel.MOCK, twoDay.getChannel());
        assertEquals(checkInAt.minusSeconds(48 * 60 * 60), twoDay.getScheduledAt());
        assertEquals(checkInAt.minusSeconds(24 * 60 * 60), paymentRequest.getScheduledAt());
        assertEquals(checkInAt.minusSeconds(12 * 60 * 60), paymentReminder.getScheduledAt());

        notificationService.reconcileBooking(java.util.UUID.fromString(bookingId));
        notificationService.reconcileAll();
        assertEquals(4, notificationRepository.findAllByBookingId(java.util.UUID.fromString(bookingId)).size());

        notificationService.deliverDueNotifications(twoDay.getScheduledAt().plusSeconds(1));
        notificationService.deliverDueNotifications(paymentRequest.getScheduledAt().plusSeconds(1));
        notificationService.deliverDueNotifications(paymentReminder.getScheduledAt().plusSeconds(1));
        assertEquals(NotificationStatus.SENT, notification(bookingId, NotificationType.TWO_DAY_REMINDER).getStatus());
        assertEquals(NotificationStatus.SENT,
                notification(bookingId, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST).getStatus());
        assertEquals(NotificationStatus.SENT, notification(bookingId, NotificationType.PAYMENT_REMINDER).getStatus());
        notificationService.deliverDueNotifications(paymentReminder.getScheduledAt().plusSeconds(2));
        assertEquals(4, notificationRepository.findAllByBookingId(java.util.UUID.fromString(bookingId)).size());

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].guestId").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }

    @Test
    void paymentAndCancellationStateSuppressStaleNotifications() throws Exception {
        String hostToken = register("phase6-payment@example.com", "Payment Host");
        String propertyId = createProperty(hostToken, "Payment Property");
        String guestId = createGuest(hostToken, "Payment Guest");
        LocalDate checkIn = LocalDate.now().plusDays(15);
        String paidBookingId = createBooking(hostToken, propertyId, guestId, checkIn, checkIn.plusDays(2),
                "PENDING_PAYMENT");
        Notification paymentRequest = notification(paidBookingId, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST);
        Notification paymentReminder = notification(paidBookingId, NotificationType.PAYMENT_REMINDER);

        String paymentId = initiate(hostToken, paidBookingId);
        String providerReference = payment(hostToken, paymentId).get("providerReference").asText();
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(providerReference, "phase6-paid", true, null)))
                .andExpect(status().isNoContent());
        assertEquals(NotificationStatus.CANCELLED,
                notification(paidBookingId, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST).getStatus());
        assertEquals(NotificationStatus.CANCELLED, notification(paidBookingId, NotificationType.PAYMENT_REMINDER).getStatus());
        notificationService.deliverDueNotifications(paymentRequest.getScheduledAt().plusSeconds(1));
        notificationService.deliverDueNotifications(paymentReminder.getScheduledAt().plusSeconds(1));
        assertEquals(NotificationStatus.CANCELLED,
                notification(paidBookingId, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST).getStatus());

        String cancelledBookingId = createBooking(hostToken, propertyId, guestId, checkIn.plusDays(4),
                checkIn.plusDays(6), "PENDING_PAYMENT");
        Notification cancelledReminder = notification(cancelledBookingId, NotificationType.TWO_DAY_REMINDER);
        mockMvc.perform(delete("/api/bookings/{bookingId}", cancelledBookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isNoContent());
        notificationService.deliverDueNotifications(cancelledReminder.getScheduledAt().plusSeconds(1));
        assertEquals(NotificationStatus.CANCELLED,
                notification(cancelledBookingId, NotificationType.TWO_DAY_REMINDER).getStatus());
    }

    @Test
    void checkoutReminderUsesCentralAvailabilityAndReconciliationRestoresMissingSchedule() throws Exception {
        String hostToken = register("phase6-checkout@example.com", "Checkout Host");
        String propertyId = createProperty(hostToken, "Checkout Property");
        String guestId = createGuest(hostToken, "Checkout Guest");
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = checkIn.plusDays(2);
        String bookingId = createBooking(hostToken, propertyId, guestId, checkIn, checkOut, "CONFIRMED");

        Notification checkout = notification(bookingId, NotificationType.CHECKOUT_REMINDER);
        assertEquals(checkOut.atTime(10, 0).toInstant(ZoneOffset.UTC).minusSeconds(60 * 60), checkout.getScheduledAt());
        notificationService.deliverDueNotifications(checkout.getScheduledAt().plusSeconds(1));
        Notification sentCheckout = notification(bookingId, NotificationType.CHECKOUT_REMINDER);
        assertEquals(NotificationStatus.SENT, sentCheckout.getStatus());
        assertTrue(Boolean.TRUE.equals(sentCheckout.getExtensionAvailable()));

        String reconciliationBookingId = createBooking(hostToken, propertyId, guestId, checkIn.plusDays(5),
                checkOut.plusDays(5), "PENDING_PAYMENT");
        Notification missing = notification(reconciliationBookingId, NotificationType.TWO_DAY_REMINDER);
        notificationRepository.delete(missing);
        notificationRepository.flush();
        assertFalse(notificationRepository.findByBookingIdAndType(java.util.UUID.fromString(reconciliationBookingId),
                NotificationType.TWO_DAY_REMINDER).isPresent());
        notificationService.reconcileAll();
        assertTrue(notificationRepository.findByBookingIdAndType(java.util.UUID.fromString(reconciliationBookingId),
                NotificationType.TWO_DAY_REMINDER).isPresent());
    }

    @Test
    void reconciliationSchedulesOnlyFutureTriggersAndNeverBackfillsMissedReminders() throws Exception {
        String hostToken = register("phase6-future-only@example.com", "Future Only Host");
        String propertyId = createProperty(hostToken, "Future Only Property");
        String guestId = createGuest(hostToken, "Future Only Guest");
        LocalDate checkIn = LocalDate.of(2030, 5, 10);
        String bookingId = createBooking(hostToken, propertyId, guestId, checkIn, checkIn.plusDays(2), "PENDING_PAYMENT");
        java.util.UUID bookingUuid = java.util.UUID.fromString(bookingId);

        clearNotifications(bookingUuid);
        notificationService.reconcileBooking(bookingUuid, Instant.parse("2030-05-10T08:00:00Z"));
        assertFalse(notificationRepository.findByBookingIdAndType(bookingUuid, NotificationType.TWO_DAY_REMINDER)
                .isPresent());
        assertFalse(notificationRepository.findByBookingIdAndType(bookingUuid,
                NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST).isPresent());
        assertFalse(notificationRepository.findByBookingIdAndType(bookingUuid, NotificationType.PAYMENT_REMINDER)
                .isPresent());
        assertEquals(Instant.parse("2030-05-12T09:00:00Z"),
                notification(bookingId, NotificationType.CHECKOUT_REMINDER).getScheduledAt());

        clearNotifications(bookingUuid);
        notificationService.reconcileBooking(bookingUuid, Instant.parse("2030-05-08T20:00:00Z"));
        assertFalse(notificationRepository.findByBookingIdAndType(bookingUuid, NotificationType.TWO_DAY_REMINDER)
                .isPresent());
        assertEquals(Instant.parse("2030-05-09T14:00:00Z"),
                notification(bookingId, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST).getScheduledAt());

        clearNotifications(bookingUuid);
        notificationService.reconcileBooking(bookingUuid, Instant.parse("2030-05-08T13:00:00Z"));
        assertEquals(Instant.parse("2030-05-08T14:00:00Z"),
                notification(bookingId, NotificationType.TWO_DAY_REMINDER).getScheduledAt());
        assertEquals(Instant.parse("2030-05-09T14:00:00Z"),
                notification(bookingId, NotificationType.TWENTY_FOUR_HOUR_PAYMENT_REQUEST).getScheduledAt());
    }

    @Test
    void legitimatelyScheduledReminderRemainsDeliverableAfterItBecomesDue() throws Exception {
        String hostToken = register("phase6-due@example.com", "Due Host");
        String propertyId = createProperty(hostToken, "Due Property");
        String guestId = createGuest(hostToken, "Due Guest");
        LocalDate checkIn = LocalDate.of(2030, 6, 10);
        String bookingId = createBooking(hostToken, propertyId, guestId, checkIn, checkIn.plusDays(2), "PENDING_PAYMENT");
        java.util.UUID bookingUuid = java.util.UUID.fromString(bookingId);
        Instant trigger = Instant.parse("2030-06-08T14:00:00Z");

        notificationService.reconcileBooking(bookingUuid, trigger.plusSeconds(1));
        assertEquals(NotificationStatus.PENDING, notification(bookingId, NotificationType.TWO_DAY_REMINDER).getStatus());
        notificationService.deliverDueNotifications(trigger.plusSeconds(1));
        assertEquals(NotificationStatus.SENT, notification(bookingId, NotificationType.TWO_DAY_REMINDER).getStatus());
    }

    @Test
    void bookingDateChangeReschedulesPendingNotifications() throws Exception {
        String hostToken = register("phase6-reschedule@example.com", "Reschedule Host");
        String propertyId = createProperty(hostToken, "Reschedule Property");
        String guestId = createGuest(hostToken, "Reschedule Guest");
        LocalDate originalCheckIn = LocalDate.now().plusDays(18);
        String bookingId = createBooking(hostToken, propertyId, guestId, originalCheckIn, originalCheckIn.plusDays(2),
                "PENDING_PAYMENT");
        Instant originalSchedule = notification(bookingId, NotificationType.TWO_DAY_REMINDER).getScheduledAt();

        LocalDate revisedCheckIn = originalCheckIn.plusDays(3);
        mockMvc.perform(put("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingPayload(propertyId, guestId, revisedCheckIn,
                                revisedCheckIn.plusDays(2), "PENDING_PAYMENT"))))
                .andExpect(status().isOk());

        Notification rescheduled = notification(bookingId, NotificationType.TWO_DAY_REMINDER);
        assertEquals(revisedCheckIn.atTime(14, 0).toInstant(ZoneOffset.UTC).minusSeconds(48 * 60 * 60),
                rescheduled.getScheduledAt());
        assertFalse(originalSchedule.equals(rescheduled.getScheduledAt()));
        assertEquals(NotificationStatus.PENDING, rescheduled.getStatus());
    }

    @Test
    void notificationEndpointsAreTenantScoped() throws Exception {
        String ownerToken = register("phase6-owner@example.com", "Notification Owner");
        String propertyId = createProperty(ownerToken, "Owner Property");
        String guestId = createGuest(ownerToken, "Owner Guest");
        String bookingId = createBooking(ownerToken, propertyId, guestId, LocalDate.now().plusDays(25),
                LocalDate.now().plusDays(27), "PENDING_PAYMENT");
        Notification ownerNotification = notification(bookingId, NotificationType.TWO_DAY_REMINDER);

        String otherToken = register("phase6-other@example.com", "Other Host");
        mockMvc.perform(get("/api/notifications/{notificationId}", ownerNotification.getId())
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/{bookingId}/notifications", bookingId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    private Notification notification(String bookingId, NotificationType type) {
        return notificationRepository.findByBookingIdAndType(java.util.UUID.fromString(bookingId), type).orElseThrow();
    }

    private void clearNotifications(java.util.UUID bookingId) {
        notificationRepository.findAllByBookingId(bookingId).forEach(notificationRepository::delete);
        notificationRepository.flush();
    }

    private String register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "passwordConfirmation", PASSWORD, "fullName", fullName, "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn();
        return TestSessionTokens.from(result);
    }

    private String createProperty(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/properties").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propertyPayload(name))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String createGuest(String token, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/guests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", fullName, "phone", "+254722333444",
                                "email", fullName.toLowerCase().replace(' ', '.') + "@example.com"))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String createBooking(String token, String propertyId, String guestId, LocalDate checkIn, LocalDate checkOut,
            String statusValue) throws Exception {
        Map<String, Object> payload = bookingPayload(propertyId, guestId, checkIn, checkOut, statusValue);
        MvcResult result = mockMvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn();
        String bookingId = json(result).get("id").asText();
        String guestToken = json(mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
        mockMvc.perform(put("/api/public/guest/{token}/registration", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Registered Guest\",\"phone\":\"+254722333444\",\"email\":\"registered."
                                + bookingId + "@example.com\"}"))
                .andExpect(status().isNoContent());
        return bookingId;
    }

    private Map<String, Object> bookingPayload(String propertyId, String guestId, LocalDate checkIn, LocalDate checkOut,
            String statusValue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", checkIn.toString());
        payload.put("checkOutDate", checkOut.toString());
        payload.put("totalAmount", new BigDecimal("450.00"));
        payload.put("currency", "KES");
        payload.put("status", statusValue);
        return payload;
    }

    private String initiate(String token, String bookingId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MPESA\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private JsonNode payment(String token, String paymentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return json(result);
    }

    private String webhookPayload(String providerReference, String eventId, boolean success, String failureReason)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerReference", providerReference);
        payload.put("eventId", eventId);
        payload.put("success", success);
        payload.put("failureReason", failureReason);
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> propertyPayload(String name) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("propertyType", "APARTMENT");
        payload.put("address", "1 Test Street, Nairobi");
        payload.put("mapsUrl", "https://maps.google.com/?q=test");
        payload.put("maxGuests", 4);
        payload.put("defaultNightlyRate", 120.00);
        payload.put("currency", "KES");
        payload.put("checkInTime", LocalTime.of(14, 0).toString());
        payload.put("checkOutTime", LocalTime.of(10, 0).toString());
        payload.put("active", true);
        return payload;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
