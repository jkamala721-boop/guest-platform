package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.guest_platform.repository.HostPayoutSettingsRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HostPayoutSettingsIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HostPayoutSettingsRepository payoutSettingsRepository;

    @Test
    void hostCanManageOnlyOwnMaskedPayoutSettingsWithoutDuplicateSubaccounts() throws Exception {
        String firstHost = register("payout-first@example.com", "First Payout Host");
        String secondHost = register("payout-second@example.com", "Second Payout Host");

        mockMvc.perform(get("/api/me/payout-settings").header("Authorization", bearer(firstHost)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(firstHost))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("0123456789")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedAccountNumber").value("****6789"))
                .andExpect(jsonPath("$.paystackSubaccountCode").doesNotExist());
        String initialSubaccount = payoutSettingsRepository.findAll().stream()
                .filter(settings -> settings.getAccountName().equals("First Payout Account"))
                .findFirst().orElseThrow().getPaystackSubaccountCode();

        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(firstHost))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("9876543210")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maskedAccountNumber").value("****3210"));
        assertThat(payoutSettingsRepository.findAll().stream()
                .filter(settings -> settings.getAccountName().equals("First Payout Account")).toList()).hasSize(1);
        assertThat(payoutSettingsRepository.findAll().stream()
                .filter(settings -> settings.getAccountName().equals("First Payout Account")).findFirst().orElseThrow()
                .getPaystackSubaccountCode()).isEqualTo(initialSubaccount);

        mockMvc.perform(get("/api/me/payout-settings").header("Authorization", bearer(secondHost)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void paystackInitiationRequiresTheBookingOwnersConfiguredPayoutDestination() throws Exception {
        String host = register("payout-required@example.com", "Payout Required Host");
        String propertyId = createProperty(host);
        String guestId = createGuest(host);
        String bookingId = createBooking(host, propertyId, guestId);

        mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId).header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"PAYSTACK\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The host must configure payout settings before accepting Paystack payments"));
    }

    private String settings(String accountNumber) throws Exception {
        return objectMapper.writeValueAsString(Map.of("payoutMethod", "BANK_ACCOUNT", "settlementBankCode", "KEPSS-TEST",
                "accountNumber", accountNumber, "accountName", "First Payout Account"));
    }

    private String register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "passwordConfirmation", PASSWORD, "fullName", fullName, "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("accessToken").asText();
    }

    private String createProperty(String token) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Payout Property");
        payload.put("propertyType", "APARTMENT");
        payload.put("address", "1 Test Street, Nairobi");
        payload.put("mapsUrl", "https://maps.google.com/?q=test");
        payload.put("maxGuests", 4);
        payload.put("defaultNightlyRate", 120.00);
        payload.put("currency", "KES");
        payload.put("checkInTime", "14:00:00");
        payload.put("checkOutTime", "10:00:00");
        payload.put("active", true);
        MvcResult result = mockMvc.perform(post("/api/properties").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String createGuest(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/guests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Payout Guest", "phone", "+254722333444",
                                "email", "payout.guest@example.com"))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String createBooking(String token, String propertyId, String guestId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", LocalDate.now().plusDays(220).toString());
        payload.put("checkOutDate", LocalDate.now().plusDays(222).toString());
        payload.put("totalAmount", new BigDecimal("3500.00"));
        payload.put("currency", "KES");
        payload.put("status", "PENDING_PAYMENT");
        MvcResult result = mockMvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
