package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.entity.Booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingCheckinLocationIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void bookingCheckinLocationCreatesUpdatesNormalizesAndIsGuestVisible() throws Exception {
        String hostToken = register();
        String propertyId = createProperty(hostToken);
        String guestId = createGuest(hostToken);
        Map<String, Object> payload = bookingPayload(propertyId, guestId);
        payload.put("houseNumber", "  Unit 4B  ");
        payload.put("blockName", "  West Wing  ");
        payload.put("guestAccessPolicy", "BEFORE_PAYMENT");

        MvcResult created = mvc.perform(post("/api/bookings").header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.houseNumber").value("Unit 4B"))
                .andExpect(jsonPath("$.blockName").value("West Wing"))
                .andReturn();
        String bookingId = body(created).get("id").asText();

        String guestToken = body(mvc.perform(post("/api/bookings/{id}/guest-link", bookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
        mvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayAccess.houseNumber").value("Unit 4B"))
                .andExpect(jsonPath("$.stayAccess.blockName").value("West Wing"))
                .andExpect(jsonPath("$.stayAccess.notes").doesNotExist());

        payload.put("status", "PENDING_PAYMENT");
        payload.remove("guestId");
        payload.remove("guestAccessPolicy");
        payload.put("houseNumber", "Villa 7");
        payload.put("blockName", "Tower 2");
        mvc.perform(put("/api/bookings/{id}", bookingId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").value("Villa 7"))
                .andExpect(jsonPath("$.blockName").value("Tower 2"));

        payload.put("houseNumber", "   ");
        payload.put("blockName", "\t");
        mvc.perform(put("/api/bookings/{id}", bookingId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").doesNotExist())
                .andExpect(jsonPath("$.blockName").doesNotExist());
        mvc.perform(get("/api/bookings/{id}", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").doesNotExist())
                .andExpect(jsonPath("$.blockName").doesNotExist());

        payload.put("houseNumber", "X".repeat(101));
        mvc.perform(post("/api/bookings").header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entitySettersEnforceNormalizationAndLengthIndependentlyOfBeanValidation() {
        Booking booking = new Booking(null, null);
        booking.setHouseNumber("  A12  ");
        booking.setBlockName("   ");
        assertThat(booking.getHouseNumber()).isEqualTo("A12");
        assertThat(booking.getBlockName()).isNull();
        assertThatThrownBy(() -> booking.setHouseNumber("X".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> booking.setBlockName("X".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payload = Map.of("email", "booking-location-" + suffix + "@example.com",
                "password", PASSWORD, "passwordConfirmation", PASSWORD, "fullName", "Location Host",
                "phone", "+254711111111");
        return TestSessionTokens.from(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn());
    }

    private String createProperty(String token) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Check-in Location Property");
        payload.put("propertyType", "APARTMENT");
        payload.put("address", "1 Test Street, Nairobi");
        payload.put("mapsUrl", "https://maps.google.com/?q=check-in-location-property");
        payload.put("maxGuests", 4);
        payload.put("defaultNightlyRate", 120.00);
        payload.put("currency", "KES");
        payload.put("checkInTime", LocalTime.of(14, 0).toString());
        payload.put("checkOutTime", LocalTime.of(10, 0).toString());
        payload.put("active", true);
        return body(mvc.perform(post("/api/properties").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String createGuest(String token) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payload = Map.of("fullName", "Location Guest", "phone", "+254722333444",
                "email", "location-guest-" + suffix + "@example.com");
        return body(mvc.perform(post("/api/guests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private Map<String, Object> bookingPayload(String propertyId, String guestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", LocalDate.now().plusDays(120).toString());
        payload.put("checkOutDate", LocalDate.now().plusDays(122).toString());
        payload.put("totalAmount", new BigDecimal("450.00"));
        payload.put("currency", "KES");
        payload.put("status", "PENDING_PAYMENT");
        return payload;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
