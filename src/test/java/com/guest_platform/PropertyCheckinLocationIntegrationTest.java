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
import java.util.Arrays;
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

import com.guest_platform.dto.BookingCreateRequest;
import com.guest_platform.dto.BookingUpdateRequest;
import com.guest_platform.entity.Property;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PropertyCheckinLocationIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void propertyLocationCreatesUpdatesNormalizesAndFlowsToGuestStayWithoutBookingDuplication() throws Exception {
        String hostToken = register();
        Map<String, Object> propertyPayload = propertyPayload();
        propertyPayload.put("houseNumber", "  Unit 4B  ");
        propertyPayload.put("blockName", "  West Wing  ");
        MvcResult createdProperty = mvc.perform(post("/api/properties").header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(propertyPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.houseNumber").value("Unit 4B"))
                .andExpect(jsonPath("$.blockName").value("West Wing"))
                .andReturn();
        String propertyId = body(createdProperty).get("id").asText();
        String guestId = createGuest(hostToken);

        Map<String, Object> bookingPayload = bookingPayload(propertyId, guestId);
        MvcResult createdBooking = mvc.perform(post("/api/bookings").header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bookingPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.houseNumber").doesNotExist())
                .andExpect(jsonPath("$.blockName").doesNotExist())
                .andReturn();
        String bookingId = body(createdBooking).get("id").asText();
        String guestToken = body(mvc.perform(post("/api/bookings/{id}/guest-link", bookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();

        mvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayAccess.houseNumber").value("Unit 4B"))
                .andExpect(jsonPath("$.stayAccess.blockName").value("West Wing"))
                .andExpect(jsonPath("$.stayAccess.notes").doesNotExist());

        propertyPayload.put("houseNumber", "Villa 7");
        propertyPayload.put("blockName", "Tower 2");
        mvc.perform(put("/api/properties/{id}", propertyId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(propertyPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").value("Villa 7"))
                .andExpect(jsonPath("$.blockName").value("Tower 2"));
        mvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayAccess.houseNumber").value("Villa 7"))
                .andExpect(jsonPath("$.stayAccess.blockName").value("Tower 2"));

        propertyPayload.put("houseNumber", "   ");
        propertyPayload.put("blockName", "\t");
        mvc.perform(put("/api/properties/{id}", propertyId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(propertyPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").doesNotExist())
                .andExpect(jsonPath("$.blockName").doesNotExist());
        mvc.perform(get("/api/properties/{id}", propertyId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").doesNotExist())
                .andExpect(jsonPath("$.blockName").doesNotExist());

        propertyPayload.put("houseNumber", "X".repeat(101));
        mvc.perform(put("/api/properties/{id}", propertyId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(propertyPayload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propertySettersEnforceLimitsAndBookingRequestsHaveNoLocationComponents() {
        Property property = new Property(null);
        property.setHouseNumber("  A12  ");
        property.setBlockName("   ");
        assertThat(property.getHouseNumber()).isEqualTo("A12");
        assertThat(property.getBlockName()).isNull();
        assertThatThrownBy(() -> property.setHouseNumber("X".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> property.setBlockName("X".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Arrays.stream(BookingCreateRequest.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .doesNotContain("houseNumber", "blockName");
        assertThat(Arrays.stream(BookingUpdateRequest.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .doesNotContain("houseNumber", "blockName");
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payload = Map.of("email", "property-location-" + suffix + "@example.com",
                "password", PASSWORD, "passwordConfirmation", PASSWORD, "fullName", "Location Host",
                "phone", "+254711111111");
        return TestSessionTokens.from(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn());
    }

    private Map<String, Object> propertyPayload() {
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
        return payload;
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
        payload.put("guestAccessPolicy", "BEFORE_PAYMENT");
        return payload;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
