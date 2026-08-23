package com.guest_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseFiveIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";
    private static final String MPESA_WEBHOOK_SECRET = "phase4-test-mpesa-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sameGuestLinkUpdatesRegistrationThenBecomesThePaidStayPage() throws Exception {
        String hostToken = register("phase5-stay@example.com", "Stay Page Host");
        String propertyId = createProperty(hostToken, "Stay Page Property");
        LocalDate checkIn = LocalDate.now().plusDays(120);
        LocalDate checkOut = checkIn.plusDays(3);
        String bookingId = createBooking(hostToken, propertyId, null, checkIn, checkOut, "PENDING_CONFIRMATION");
        String guestToken = createGuestLink(hostToken, bookingId);
        String expectedExpiry = checkOut.atTime(10, 0).toInstant(ZoneOffset.UTC).toString();

        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REGISTRATION_OR_PAYMENT"))
                .andExpect(jsonPath("$.expiresAt").value(expectedExpiry))
                .andExpect(jsonPath("$.property.name").value("Stay Page Property"))
                .andExpect(jsonPath("$.property.location").value("1 Test Street, Nairobi"))
                .andExpect(jsonPath("$.stay.checkInDate").value(checkIn.toString()))
                .andExpect(jsonPath("$.stay.checkOutDate").value(checkOut.toString()))
                .andExpect(jsonPath("$.payment.amount").value(450.00))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.property.wifiPassword").doesNotExist())
                .andExpect(jsonPath("$.property.checkInInstructions").doesNotExist())
                .andExpect(jsonPath("$.bookingId").doesNotExist())
                .andExpect(jsonPath("$.guestId").doesNotExist());

        Map<String, Object> registration = new LinkedHashMap<>();
        registration.put("fullName", "Registered Guest");
        registration.put("phone", "+254733444555");
        registration.put("email", "registered.guest@example.com");
        registration.put("idType", "PASSPORT");
        registration.put("idNumber", "P1234567");
        registration.put("nationality", "Kenyan");
        registration.put("whatsappNumber", "+254733444555");
        mockMvc.perform(put("/api/public/guest/{token}/registration", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isNoContent());
        String registeredGuestId = json(mockMvc.perform(get("/api/bookings/{bookingId}", bookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.guestId").isNotEmpty())
                .andReturn()).get("guestId").asText();
        mockMvc.perform(get("/api/guests/{guestId}", registeredGuestId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Registered Guest"))
                .andExpect(jsonPath("$.email").value("registered.guest@example.com"))
                .andExpect(jsonPath("$.idNumber").value("P1234567"));
        mockMvc.perform(put("/api/public/guest/{token}/registration", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"phone\":\"+2547\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/guest/{token}/receipt", guestToken)).andExpect(status().isNotFound());

        String paymentId = initiatePublic(guestToken);
        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REGISTRATION_OR_PAYMENT"))
                .andExpect(jsonPath("$.payment.status").value("PROCESSING"))
                .andExpect(jsonPath("$.property.wifiPassword").doesNotExist());

        String providerReference = payment(hostToken, paymentId).get("providerReference").asText();
        String successPayload = webhookPayload(providerReference, "phase5-stay-success", true, null);
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(successPayload))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STAY_ACTIVE"))
                .andExpect(jsonPath("$.bookingConfirmed").value(true))
                .andExpect(jsonPath("$.property.mapsUrl").value("https://maps.google.com/?q=test"))
                .andExpect(jsonPath("$.property.wifiName").value("Hostvero WiFi"))
                .andExpect(jsonPath("$.property.wifiPassword").value("guest-only-password"))
                .andExpect(jsonPath("$.property.checkInInstructions").value("Use the blue gate"))
                .andExpect(jsonPath("$.property.houseRules").value("No smoking"))
                .andExpect(jsonPath("$.property.contactPhone").value("+254700000000"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.receipt.available").value(true))
                .andExpect(jsonPath("$.receipt.receiptNumber").exists())
                .andExpect(jsonPath("$.bookingId").doesNotExist())
                .andExpect(jsonPath("$.guestId").doesNotExist());
        mockMvc.perform(get("/api/public/guest/{token}/receipt", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").exists())
                .andExpect(jsonPath("$.bookingId").doesNotExist());

        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(successPayload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/receipts").header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/public/guest/{token}/registration", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isConflict());
    }

    @Test
    void failedOrLatePaymentNeverActivatesGuestLink() throws Exception {
        String hostToken = register("phase5-failed@example.com", "Failure Host");
        String propertyId = createProperty(hostToken, "Failure Property");
        String guestId = createGuest(hostToken, "Failure Guest");

        String failedBookingId = createBooking(hostToken, propertyId, guestId, LocalDate.now().plusDays(130),
                LocalDate.now().plusDays(132), "PENDING_PAYMENT");
        String failedGuestToken = createGuestLink(hostToken, failedBookingId);
        String failedPaymentId = initiate(hostToken, failedBookingId);
        String failedReference = payment(hostToken, failedPaymentId).get("providerReference").asText();
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(failedReference, "phase5-failed-payment", false, "Declined")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/guest/{token}", failedGuestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REGISTRATION_OR_PAYMENT"))
                .andExpect(jsonPath("$.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.property.wifiPassword").doesNotExist());

        String cancelledBookingId = createBooking(hostToken, propertyId, guestId, LocalDate.now().plusDays(135),
                LocalDate.now().plusDays(137), "PENDING_PAYMENT");
        String cancelledGuestToken = createGuestLink(hostToken, cancelledBookingId);
        String cancelledPaymentId = initiate(hostToken, cancelledBookingId);
        String cancelledReference = payment(hostToken, cancelledPaymentId).get("providerReference").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/bookings/{bookingId}", cancelledBookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(cancelledReference, "phase5-late-success", true, null)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bookings/{bookingId}", cancelledBookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/public/guest/{token}", cancelledGuestToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicStripeInitiationRequiresRegistrationUsesBookingAmountAndRejectsUnknownLinks() throws Exception {
        String hostToken = register("phase5-public-stripe@example.com", "Public Stripe Host");
        String propertyId = createProperty(hostToken, "Public Stripe Property");
        String bookingId = createBooking(hostToken, propertyId, null, LocalDate.now().plusDays(145),
                LocalDate.now().plusDays(147), "PENDING_PAYMENT");
        String guestToken = createGuestLink(hostToken, bookingId);

        mockMvc.perform(post("/api/public/guest/{token}/payments", guestToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"STRIPE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/public/guest/{token}/payments", "unknown-link")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"STRIPE\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/public/guest/{token}/registration", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Stripe Public Guest\",\"phone\":\"+254733444556\",\"email\":\"stripe.public.guest@example.com\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/public/guest/{token}/payments", guestToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"STRIPE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.amount").value(450.00))
                .andExpect(jsonPath("$.currency").value("KES"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void expiredAndRevokedGuestLinksNeverExposePublicStayData() throws Exception {
        String hostToken = register("phase5-expired@example.com", "Expiry Host");
        String propertyId = createProperty(hostToken, "Expiry Property");
        String guestId = createGuest(hostToken, "Expiry Guest");

        String expiredBookingId = createBooking(hostToken, propertyId, guestId, LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(3), "PENDING_PAYMENT");
        String expiredGuestToken = createGuestLink(hostToken, expiredBookingId);
        mockMvc.perform(get("/api/public/guest/{token}", expiredGuestToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/guest/{token}/receipt", expiredGuestToken)).andExpect(status().isNotFound());

        String currentBookingId = createBooking(hostToken, propertyId, guestId, LocalDate.now().plusDays(140),
                LocalDate.now().plusDays(142), "PENDING_PAYMENT");
        String revokedGuestToken = createGuestLink(hostToken, currentBookingId);
        String currentGuestToken = createGuestLink(hostToken, currentBookingId);
        mockMvc.perform(get("/api/public/guest/{token}", revokedGuestToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/guest/{token}/receipt", revokedGuestToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/guest/{token}", currentGuestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REGISTRATION_OR_PAYMENT"));
    }

    private String register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "passwordConfirmation", PASSWORD, "fullName", fullName, "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("accessToken").asText();
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
            String status) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", checkIn.toString());
        payload.put("checkOutDate", checkOut.toString());
        payload.put("totalAmount", new BigDecimal("450.00"));
        payload.put("currency", "KES");
        payload.put("status", status);
        MvcResult result = mockMvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String createGuestLink(String token, String bookingId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("token").asText();
    }

    private String initiate(String token, String bookingId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MPESA\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String initiatePublic(String guestToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/public/guest/{token}/payments", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
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
        payload.put("wifiName", "Hostvero WiFi");
        payload.put("wifiPassword", "guest-only-password");
        payload.put("houseRules", "No smoking");
        payload.put("checkInInstructions", "Use the blue gate");
        payload.put("contactPhone", "+254700000000");
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
