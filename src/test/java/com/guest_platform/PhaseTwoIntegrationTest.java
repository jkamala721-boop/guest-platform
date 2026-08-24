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
class PhaseTwoIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedHostCanCreateListReadUpdateAndDeleteOwnGuest() throws Exception {
        String ownerToken = register("phase2-owner@example.com", "Phase Two Owner");
        String guestId = createGuest(ownerToken, "Ada Guest");

        mockMvc.perform(get("/api/guests/{guestId}", guestId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idNumber").value("A1234567"))
                .andExpect(jsonPath("$.hostId").doesNotExist());

        Map<String, Object> update = guestPayload("Ada Updated");
        update.put("notes", "Late arrival requested");
        mockMvc.perform(put("/api/guests/{guestId}", guestId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ada Updated"))
                .andExpect(jsonPath("$.notes").value("Late arrival requested"));

        mockMvc.perform(delete("/api/guests/{guestId}", guestId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/guests/{guestId}", guestId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listWithoutOptionalFiltersReturnsExistingGuestsForAuthenticatedHost() throws Exception {
        String token = register("phase2-list-unfiltered@example.com", "Unfiltered List Host");
        String guestId = createGuest(token, "Unfiltered Guest");

        mockMvc.perform(get("/api/guests").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(guestId))
                .andExpect(jsonPath("$[0].fullName").value("Unfiltered Guest"))
                .andExpect(jsonPath("$[0].idNumber").doesNotExist())
                .andExpect(jsonPath("$[0].notes").doesNotExist());
    }

    @Test
    void listSupportsOptionalFiltersForAuthenticatedHostsGuests() throws Exception {
        String token = register("phase2-list-filtered@example.com", "Filtered List Host");
        String matchingGuestId = createGuest(token, "Ada Filtered");
        createGuest(token, "Other Guest");

        mockMvc.perform(get("/api/guests")
                        .queryParam("query", "filtered")
                        .queryParam("nationality", "kenyan")
                        .queryParam("idType", "passport")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(matchingGuestId))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void guestEndpointsRejectUnauthenticatedAndInvalidRequests() throws Exception {
        mockMvc.perform(get("/api/guests"))
                .andExpect(status().isUnauthorized());

        String token = register("phase2-validation@example.com", "Validation Owner");
        Map<String, Object> invalid = guestPayload(" ");
        invalid.put("email", "not-an-email");
        invalid.put("phone", "");
        mockMvc.perform(post("/api/guests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.fullName").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.phone").exists());
    }

    @Test
    void anotherHostCannotReadUpdateDeleteOrListAnOwnersGuests() throws Exception {
        String ownerToken = register("phase2-guest-owner@example.com", "Guest Owner");
        String guestId = createGuest(ownerToken, "Private Guest");
        String otherToken = register("phase2-guest-other@example.com", "Other Guest Host");

        mockMvc.perform(get("/api/guests").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/guests/{guestId}", guestId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/guests/{guestId}", guestId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(guestPayload("Attempted Takeover"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/guests/{guestId}", guestId).header("Authorization", bearer(otherToken)))
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
        return TestSessionTokens.from(result);
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

    private Map<String, Object> guestPayload(String fullName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fullName", fullName);
        payload.put("phone", "+254722333444");
        payload.put("email", "ada.guest@example.com");
        payload.put("idType", "PASSPORT");
        payload.put("idNumber", "A1234567");
        payload.put("nationality", "Kenyan");
        payload.put("whatsappNumber", "+254722333444");
        payload.put("notes", "Vegetarian breakfast preferred");
        return payload;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
