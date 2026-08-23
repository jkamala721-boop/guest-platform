package com.guest_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class PhaseOneIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationLoginAndProfileManagementWorkWithoutExposingPasswordHashes() throws Exception {
        String token = register("host@example.com", "Original Host");

        mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("host@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(put("/api/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Updated Host",
                                "phone", "+254700000000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Host"))
                .andExpect(jsonPath("$.phone").value("+254700000000"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "HOST@example.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "host@example.com", "password", "wrong password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void propertyCrudIsScopedToTheAuthenticatedHostAndDeleteDeactivates() throws Exception {
        String ownerToken = register("owner@example.com", "Property Owner");
        String propertyId = createProperty(ownerToken, "Owner apartment");
        String otherToken = register("other@example.com", "Other Host");

        mockMvc.perform(get("/api/properties").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(propertyId))
                .andExpect(jsonPath("$[0].wifiPassword").value("private-wifi-pass"));

        mockMvc.perform(get("/api/properties/{id}", propertyId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/properties/{id}", propertyId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propertyPayload("Attempted takeover"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/properties/{id}", propertyId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/properties/{id}", propertyId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/properties/{id}", propertyId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void protectedEndpointsRejectMissingTokensAndInvalidProperties() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isUnauthorized());

        String token = register("validation@example.com", "Validation Host");
        Map<String, Object> invalid = propertyPayload("Invalid property");
        invalid.put("mapsUrl", "not-a-url");
        invalid.put("maxGuests", 0);
        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.mapsUrl").exists())
                .andExpect(jsonPath("$.validationErrors.maxGuests").exists());

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void logoutRevokesTheBearerSession() throws Exception {
        String token = register("logout@example.com", "Logout Host");
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
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
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
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
        payload.put("wifiName", "Guest Wifi");
        payload.put("wifiPassword", "private-wifi-pass");
        payload.put("houseRules", "No smoking");
        payload.put("checkInInstructions", "Use the front door");
        payload.put("contactPhone", "+254711111111");
        payload.put("active", true);
        return payload;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
