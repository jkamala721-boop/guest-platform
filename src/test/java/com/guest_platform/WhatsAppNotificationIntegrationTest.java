package com.guest_platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.entity.NotificationStatus;
import com.guest_platform.entity.NotificationType;
import com.guest_platform.repository.NotificationRepository;
import com.guest_platform.service.notification.WhatsAppTemplateMessage;
import com.guest_platform.service.notification.WhatsAppTransport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.notifications.whatsapp.enabled=false",
        "app.notifications.whatsapp.access-token=test-access-token",
        "app.notifications.whatsapp.phone-number-id=test-phone-number-id",
        "app.notifications.whatsapp.api-version=v-test",
        "app.notifications.whatsapp.manual-template-name=hostvero_manual",
        "app.notifications.whatsapp.guest-link-template-name=hostvero_guest_link",
        "app.notifications.whatsapp.scheduled-template-name=hostvero_scheduled" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WhatsAppNotificationIntegrationTest.TransportConfiguration.class)
class WhatsAppNotificationIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordingTransport transport;
    @Autowired private NotificationRepository notificationRepository;

    @BeforeEach
    void resetTransport() {
        transport.messages.clear();
        transport.fail = false;
    }

    @Test
    void manualWhatsAppIsHostScopedAndMarksSentOnlyAfterTransportAccepts() throws Exception {
        String owner = register("whatsapp-owner@example.com");
        String bookingId = booking(owner, "+254722333444", null);

        mockMvc.perform(post("/api/bookings/{bookingId}/notifications/manual", bookingId)
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"subject\":\"Welcome\",\"message\":\"Your room is ready\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.status").value("SENT"));
        assertEquals(1, transport.messages.size());
        assertEquals("254722333444", transport.messages.getFirst().recipient());
        assertEquals("hostvero_manual", transport.messages.getFirst().templateName());

        String other = register("whatsapp-other@example.com");
        mockMvc.perform(post("/api/bookings/{bookingId}/notifications/manual", bookingId)
                        .header("Authorization", bearer(other)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"subject\":\"Welcome\",\"message\":\"No access\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPhoneIsRejectedAndTransportFailurePersistsFailedStatus() throws Exception {
        String owner = register("whatsapp-failure@example.com");
        String invalidPhoneBooking = booking(owner, "0722333444", null);
        mockMvc.perform(post("/api/bookings/{bookingId}/notifications/manual", invalidPhoneBooking)
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"subject\":\"Welcome\",\"message\":\"Hello\"}"))
                .andExpect(status().isConflict());

        String deliveryFailureBooking = booking(owner, "+254722333445", null);
        transport.fail = true;
        mockMvc.perform(post("/api/bookings/{bookingId}/notifications/manual", deliveryFailureBooking)
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"subject\":\"Welcome\",\"message\":\"Hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.deliveryDetail").value("Delivery failed"));
        assertEquals(NotificationStatus.FAILED, notificationRepository.findByBookingIdAndType(
                java.util.UUID.fromString(deliveryFailureBooking), NotificationType.MANUAL_MESSAGE).orElseThrow().getStatus());
    }

    @Test
    void guestLinkWhatsAppUsesRawTokenOnlyAtDeliveryAndRequiresTheOwnedBooking() throws Exception {
        String owner = register("whatsapp-link-owner@example.com");
        String bookingId = booking(owner, "+254722333446", "+254733444555");
        String token = guestLink(owner, bookingId);

        mockMvc.perform(post("/api/bookings/{bookingId}/guest-link/whatsapp", bookingId)
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.tokenHash").doesNotExist());
        assertEquals("hostvero_guest_link", transport.messages.getFirst().templateName());
        assertTrue(transport.messages.getFirst().bodyParameters().getLast().contains("/guest/" + token));
        assertFalse(notificationRepository.findByBookingIdAndType(java.util.UUID.fromString(bookingId),
                NotificationType.GUEST_LINK).orElseThrow().getMessage().contains(token));

        String otherBooking = booking(owner, "+254722333447", null);
        mockMvc.perform(post("/api/bookings/{bookingId}/guest-link/whatsapp", otherBooking)
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNotFound());
    }

    private String register(String email) throws Exception {
        return json(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                        "passwordConfirmation", PASSWORD, "fullName", "WhatsApp Host", "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn()).get("accessToken").asText();
    }

    private String booking(String token, String phone, String whatsappNumber) throws Exception {
        String propertyId = json(mockMvc.perform(post("/api/properties").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("name", "WhatsApp Property",
                        "propertyType", "APARTMENT", "address", "1 Test Street", "mapsUrl", "https://maps.google.com/?q=test", "maxGuests", 2,
                        "defaultNightlyRate", new BigDecimal("100"), "currency", "KES", "checkInTime", LocalTime.of(14, 0).toString(),
                        "checkOutTime", LocalTime.of(10, 0).toString(), "active", true))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        Map<String, Object> guest = new java.util.LinkedHashMap<>(Map.of("fullName", "WhatsApp Guest", "phone", phone,
                "email", "whatsapp.guest." + phone.replace("+", "") + "@example.com"));
        if (whatsappNumber != null) guest.put("whatsappNumber", whatsappNumber);
        String guestId = json(mockMvc.perform(post("/api/guests").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(guest)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        MvcResult booking = mockMvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("propertyId", propertyId,
                        "guestId", guestId, "checkInDate", LocalDate.now().plusDays(14).toString(),
                        "checkOutDate", LocalDate.now().plusDays(16).toString(), "totalAmount", new BigDecimal("200"),
                        "currency", "KES", "status", "PENDING_PAYMENT"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.guestId").value(guestId)).andReturn();
        return json(booking).get("id").asText();
    }

    private String guestLink(String token, String bookingId) throws Exception {
        return json(mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                .header("Authorization", bearer(token))).andExpect(status().isCreated()).andReturn()).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
    private String bearer(String token) { return "Bearer " + token; }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransportConfiguration {
        @Bean @Primary RecordingTransport recordingTransport() { return new RecordingTransport(); }
    }

    static class RecordingTransport implements WhatsAppTransport {
        final List<WhatsAppTemplateMessage> messages = new ArrayList<>();
        volatile boolean fail;
        @Override public void send(WhatsAppTemplateMessage message) {
            if (fail) throw new IllegalStateException("Meta WhatsApp delivery failed: provider failure");
            messages.add(message);
        }
    }
}
