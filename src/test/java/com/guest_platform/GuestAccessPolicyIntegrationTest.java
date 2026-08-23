package com.guest_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
class GuestAccessPolicyIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";
    private static final String MPESA_WEBHOOK_SECRET = "phase4-test-mpesa-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void hostControlsPrePaymentStayAccessWithoutConfirmingPayment() throws Exception {
        String ownerToken = register("access-owner@example.com", "Access Owner");
        String propertyId = createProperty(ownerToken);
        String guestId = createGuest(ownerToken);
        LocalDate checkIn = LocalDate.now().plusDays(60);
        String bookingId = createBooking(ownerToken, propertyId, guestId, checkIn, checkIn.plusDays(2));
        String guestToken = guestLink(ownerToken, bookingId);

        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestAccessPolicy").value("AFTER_PAYMENT"));
        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REGISTRATION_OR_PAYMENT"))
                .andExpect(jsonPath("$.stayAccess").doesNotExist());

        String otherToken = register("access-other@example.com", "Access Other");
        mockMvc.perform(put("/api/bookings/{bookingId}/guest-access-policy", bookingId)
                        .header("Authorization", bearer(otherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policy\":\"BEFORE_PAYMENT\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/bookings/{bookingId}/guest-access-policy", bookingId)
                        .header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policy\":\"BEFORE_PAYMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestAccessPolicy").value("BEFORE_PAYMENT"));
        mockMvc.perform(put("/api/bookings/{bookingId}/guest-access-policy", bookingId)
                        .header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policy\":\"NOT_A_POLICY\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REGISTRATION_OR_PAYMENT"))
                .andExpect(jsonPath("$.stayAccess.wifiPassword").value("guest-only-password"))
                .andExpect(jsonPath("$.stayAccess.checkInInstructions").value("Use the blue gate"))
                .andExpect(jsonPath("$.receipt").doesNotExist());
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        mockMvc.perform(get("/api/public/guest/{token}/receipt", guestToken)).andExpect(status().isNotFound());

        String paymentId = json(mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MPESA\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String reference = json(mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn()).get("providerReference").asText();
        mockMvc.perform(post("/api/webhooks/mpesa").header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("providerReference", reference,
                                "eventId", "access-policy-payment", "success", true))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STAY_ACTIVE"))
                .andExpect(jsonPath("$.bookingConfirmed").value(true));
    }

    private String register(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "passwordConfirmation", PASSWORD, "fullName", name, "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("accessToken").asText();
    }

    private String createProperty(String token) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Access Property");
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
        return json(mockMvc.perform(post("/api/properties").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String createGuest(String token) throws Exception {
        return json(mockMvc.perform(post("/api/guests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Access Guest\",\"phone\":\"+254722333444\",\"email\":\"access.guest@example.com\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String createBooking(String token, String propertyId, String guestId, LocalDate checkIn, LocalDate checkOut)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", checkIn.toString());
        payload.put("checkOutDate", checkOut.toString());
        payload.put("totalAmount", new BigDecimal("450.00"));
        payload.put("currency", "KES");
        payload.put("status", "PENDING_PAYMENT");
        return json(mockMvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String guestLink(String token, String bookingId) throws Exception {
        return json(mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
