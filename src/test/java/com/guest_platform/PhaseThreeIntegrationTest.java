package com.guest_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
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

import javax.sql.DataSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseThreeIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void hostCanCreateBookingsAndAvailabilityUsesOnlyBlockingStatuses() throws Exception {
        String token = register("phase3-booking-owner@example.com", "Booking Owner");
        String propertyId = createProperty(token, "Booking Property");
        String guestId = createGuest(token, "Booking Guest");
        LocalDate start = LocalDate.now().plusDays(20);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                bookingPayload(propertyId, guestId, start, start, "CONFIRMED"))))
                .andExpect(status().isBadRequest());

        String confirmedBookingId = createBooking(token, propertyId, guestId, start, start.plusDays(2), "CONFIRMED");
        createBooking(token, propertyId, guestId, start.plusDays(2), start.plusDays(4), "PENDING_CONFIRMATION");

        mockMvc.perform(get("/api/bookings").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(get("/api/properties/{propertyId}/availability", propertyId)
                        .queryParam("checkIn", start.toString())
                        .queryParam("checkOut", start.plusDays(2).toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                bookingPayload(propertyId, guestId, start.plusDays(1), start.plusDays(3), "CONFIRMED"))))
                .andExpect(status().isConflict());

        createBooking(token, propertyId, guestId, start, start.plusDays(2), "DRAFT");
        mockMvc.perform(delete("/api/bookings/{bookingId}", confirmedBookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bookings/{bookingId}", confirmedBookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/properties/{propertyId}/availability", propertyId)
                        .queryParam("checkIn", start.toString())
                        .queryParam("checkOut", start.plusDays(2).toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void bookingAndAvailabilityEndpointsAreTenantIsolated() throws Exception {
        String ownerToken = register("phase3-owner@example.com", "Booking Owner");
        String ownerPropertyId = createProperty(ownerToken, "Owner Property");
        String ownerGuestId = createGuest(ownerToken, "Owner Guest");
        LocalDate start = LocalDate.now().plusDays(35);
        String bookingId = createBooking(ownerToken, ownerPropertyId, ownerGuestId, start, start.plusDays(2),
                "PENDING_CONFIRMATION");

        String otherToken = register("phase3-other@example.com", "Other Host");
        String otherPropertyId = createProperty(otherToken, "Other Property");
        String otherGuestId = createGuest(otherToken, "Other Guest");

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                bookingPayload(ownerPropertyId, otherGuestId, start.plusDays(5), start.plusDays(7),
                                        "PENDING_CONFIRMATION"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                bookingPayload(otherPropertyId, ownerGuestId, start.plusDays(5), start.plusDays(7),
                                        "PENDING_CONFIRMATION"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/bookings/{bookingId}", bookingId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                bookingPayload(otherPropertyId, otherGuestId, start, start.plusDays(2), "DRAFT"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/properties/{propertyId}/availability", ownerPropertyId)
                        .queryParam("checkIn", start.toString())
                        .queryParam("checkOut", start.plusDays(2).toString())
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestLinksAreHashedRotatableAndRejectInvalidExpiredOrRevokedTokens() throws Exception {
        String token = register("phase3-links@example.com", "Guest Link Host");
        String propertyId = createProperty(token, "Link Property");
        String guestId = createGuest(token, "Link Guest");
        LocalDate start = LocalDate.now().plusDays(50);
        String bookingId = createBooking(token, propertyId, guestId, start, start.plusDays(2), "PENDING_PAYMENT");

        String firstToken = createGuestLink(token, bookingId);
        mockMvc.perform(get("/api/public/guest/{token}", firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REGISTRATION_OR_PAYMENT"))
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.bookingId").doesNotExist());
        String tokenHash = sha256(firstToken);
        assertStoredTokenIsHashed(tokenHash, firstToken);

        String secondToken = createGuestLink(token, bookingId);
        mockMvc.perform(get("/api/public/guest/{token}", firstToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/guest/{token}", secondToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guest/{token}", "not-a-valid-token"))
                .andExpect(status().isNotFound());

        String expiredBookingId = createBooking(token, propertyId, guestId, LocalDate.now().minusDays(4),
                LocalDate.now().minusDays(2), "DRAFT");
        String expiredToken = createGuestLink(token, expiredBookingId);
        mockMvc.perform(get("/api/public/guest/{token}", expiredToken))
                .andExpect(status().isNotFound());
    }

    private String register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "passwordConfirmation", PASSWORD,
                                "fullName", fullName,
                                "phone", "+254711111111"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createProperty(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propertyPayload(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createGuest(String token, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/guests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(guestPayload(fullName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createBooking(String token, String propertyId, String guestId, LocalDate checkIn,
            LocalDate checkOut, String statusValue) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                bookingPayload(propertyId, guestId, checkIn, checkOut, statusValue))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createGuestLink(String token, String bookingId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
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
        payload.put("checkInTime", "14:00:00");
        payload.put("checkOutTime", "10:00:00");
        payload.put("active", true);
        return payload;
    }

    private Map<String, Object> guestPayload(String fullName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fullName", fullName);
        payload.put("phone", "+254722333444");
        payload.put("email", fullName.toLowerCase().replace(' ', '.') + "@example.com");
        return payload;
    }

    private Map<String, Object> bookingPayload(String propertyId, String guestId, LocalDate checkIn,
            LocalDate checkOut, String statusValue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", checkIn.toString());
        payload.put("checkOutDate", checkOut.toString());
        payload.put("totalAmount", 250.00);
        payload.put("currency", "KES");
        payload.put("status", statusValue);
        payload.put("notes", "Phase 3 booking");
        return payload;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertStoredTokenIsHashed(String tokenHash, String plaintextToken) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select token_hash from guest_links where token_hash = ?")) {
            statement.setString(1, tokenHash);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertNotEquals(plaintextToken, result.getString(1));
            }
        }
    }
}
