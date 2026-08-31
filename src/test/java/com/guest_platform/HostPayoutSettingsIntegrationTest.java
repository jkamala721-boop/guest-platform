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

import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.PayoutMethod;
import com.guest_platform.repository.HostPayoutSettingsRepository;
import com.guest_platform.repository.HostRepository;
import com.guest_platform.repository.PaymentRepository;

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
    @Autowired private HostRepository hostRepository;
    @Autowired private PaymentRepository paymentRepository;

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
        var initialSettings = payoutSettingsRepository.findAll().stream()
                .filter(settings -> settings.getPaystackSubaccountCode().equals(initialSubaccount))
                .findFirst().orElseThrow();
        assertThat(initialSettings.getPaystackSubaccountDomain()).isEqualTo("mock");
        assertThat(initialSettings.getPaystackSubaccountActive()).isTrue();
        assertThat(initialSettings.getPaystackSubaccountVerified()).isTrue();

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

        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(secondHost))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("1111222233")))
                .andExpect(status().isOk());
        String secondSubaccount = payoutSettingsRepository.findAll().stream()
                .filter(settings -> "****2233".equals("****" + settings.getAccountNumberLast4()))
                .findFirst().orElseThrow().getPaystackSubaccountCode();
        assertThat(secondSubaccount).isNotEqualTo(initialSubaccount);
    }

    @Test
    void bookingOwnerDeterminesStoredSplitDestinationAndClientCannotOverrideIt() throws Exception {
        String firstToken = register("split-owner-a@example.com", "Split Owner A");
        String secondToken = register("split-owner-b@example.com", "Split Owner B");
        configure(firstToken, "0123456789", "Owner A Account");
        configure(secondToken, "9876543210", "Owner B Account");
        String firstCode = payoutSettingsRepository.findAll().stream()
                .filter(value -> "Owner A Account".equals(value.getAccountName())).findFirst().orElseThrow()
                .getPaystackSubaccountCode();
        String secondCode = payoutSettingsRepository.findAll().stream()
                .filter(value -> "Owner B Account".equals(value.getAccountName())).findFirst().orElseThrow()
                .getPaystackSubaccountCode();

        String firstBooking = createBooking(firstToken, createProperty(firstToken), createGuest(firstToken),
                new BigDecimal("10000.00"));
        String secondBooking = createBooking(secondToken, createProperty(secondToken), createGuest(secondToken),
                new BigDecimal("10000.00"));
        JsonNode firstPayment = json(mockMvc.perform(post("/api/bookings/{bookingId}/payments", firstBooking)
                        .header("Authorization", bearer(firstToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"PAYSTACK\",\"hostId\":\"00000000-0000-0000-0000-000000000000\","
                                + "\"subaccount\":\"ACCT_ATTACKER\",\"transaction_charge\":1,\"amount\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingAmount").value(10000.00))
                .andExpect(jsonPath("$.serviceFee").value(500.00))
                .andExpect(jsonPath("$.chargedAmount").value(10500.00))
                .andReturn());
        JsonNode secondPayment = json(mockMvc.perform(post("/api/bookings/{bookingId}/payments", secondBooking)
                        .header("Authorization", bearer(secondToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"PAYSTACK\",\"hostId\":\"00000000-0000-0000-0000-000000000000\","
                                + "\"subaccount\":\"ACCT_ATTACKER\",\"transaction_charge\":999999,\"amount\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingAmount").value(10000.00))
                .andExpect(jsonPath("$.serviceFee").value(500.00))
                .andExpect(jsonPath("$.chargedAmount").value(10500.00))
                .andReturn());

        var persistedA = paymentRepository.findById(java.util.UUID.fromString(firstPayment.get("id").asText()))
                .orElseThrow();
        var persistedB = paymentRepository.findById(java.util.UUID.fromString(secondPayment.get("id").asText()))
                .orElseThrow();
        assertThat(persistedA.getPayoutDestinationReference()).isEqualTo(firstCode).isNotEqualTo(secondCode)
                .isNotEqualTo("ACCT_ATTACKER");
        assertThat(persistedB.getPayoutDestinationReference()).isEqualTo(secondCode).isNotEqualTo(firstCode)
                .isNotEqualTo("ACCT_ATTACKER");
        assertThat(persistedA.getBookingAmount()).isEqualByComparingTo("10000.00");
        assertThat(persistedA.getServiceFee()).isEqualByComparingTo("500.00");
        assertThat(persistedA.getAmount()).isEqualByComparingTo("10500.00");
        assertThat(persistedB.getBookingAmount()).isEqualByComparingTo("10000.00");
        assertThat(persistedB.getServiceFee()).isEqualByComparingTo("500.00");
        assertThat(persistedB.getAmount()).isEqualByComparingTo("10500.00");
        assertThat(firstCode).isNotEqualTo(secondCode);
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
                .andExpect(jsonPath("$.code").value("HOST_PAYOUT_ACCOUNT_NOT_READY"))
                .andExpect(jsonPath("$.message").value("Host payout account is not ready for automatic settlement."));
    }

    @Test
    void hostCanConfigureMaskedMpesaRecipientWithoutDuplicatingItOnRepeatSave() throws Exception {
        String host = register("payout-mpesa@example.com", "M-Pesa Payout Host");

        mockMvc.perform(get("/api/me/payout-settings/banks").header("Authorization", bearer(host)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("KEPSS-TEST"));
        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "payoutMethod", "MPESA", "mpesaPhone", "0712345678"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.payoutMethod").value("MPESA"))
                .andExpect(jsonPath("$.maskedMpesaPhone").value("****5678"))
                .andExpect(jsonPath("$.paystackRecipientCode").doesNotExist());
        String recipientCode = payoutSettingsRepository.findAll().stream()
                .filter(settings -> settings.getPayoutMethod().name().equals("MPESA"))
                .findFirst().orElseThrow().getPaystackRecipientCode();

        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "payoutMethod", "MPESA", "mpesaPhone", "+254712345678"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maskedMpesaPhone").value("****5678"));
        assertThat(payoutSettingsRepository.findAll().stream()
                .filter(settings -> settings.getPayoutMethod().name().equals("MPESA")).toList()).hasSize(1);
        assertThat(payoutSettingsRepository.findAll().stream()
                .filter(settings -> settings.getPayoutMethod().name().equals("MPESA")).findFirst().orElseThrow()
                .getPaystackRecipientCode()).isEqualTo(recipientCode);

        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "payoutMethod", "MPESA", "mpesaPhone", "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameProviderRecipientMayRepresentTheSameLegitimateDestinationForDifferentHosts() throws Exception {
        register("shared-recipient-a@example.com", "Shared Recipient A");
        register("shared-recipient-b@example.com", "Shared Recipient B");
        var first = hostRepository.findByEmailIgnoreCase("shared-recipient-a@example.com").orElseThrow();
        var second = hostRepository.findByEmailIgnoreCase("shared-recipient-b@example.com").orElseThrow();
        String sharedProviderRecipient = "RCP_SHARED_PROVIDER_DESTINATION";

        payoutSettingsRepository.saveAndFlush(new HostPayoutSettings(first, PayoutMethod.MPESA, null, null, null,
                null, sharedProviderRecipient, "5678", "a".repeat(64)));
        payoutSettingsRepository.saveAndFlush(new HostPayoutSettings(second, PayoutMethod.MPESA, null, null, null,
                null, sharedProviderRecipient, "5678", "a".repeat(64)));

        assertThat(payoutSettingsRepository.findAll().stream()
                .filter(value -> sharedProviderRecipient.equals(value.getPaystackRecipientCode())))
                .hasSize(2);
    }

    private String settings(String accountNumber) throws Exception {
        return objectMapper.writeValueAsString(Map.of("payoutMethod", "BANK_ACCOUNT", "settlementBankCode", "KEPSS-TEST",
                "accountNumber", accountNumber, "accountName", "First Payout Account"));
    }

    private void configure(String token, String accountNumber, String accountName) throws Exception {
        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "payoutMethod", "BANK_ACCOUNT", "settlementBankCode", "KEPSS-TEST",
                                "accountNumber", accountNumber, "accountName", accountName))))
                .andExpect(status().isOk());
    }

    private String register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "passwordConfirmation", PASSWORD, "fullName", fullName, "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn();
        return TestSessionTokens.from(result);
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
        return createBooking(token, propertyId, guestId, new BigDecimal("3500.00"));
    }

    private String createBooking(String token, String propertyId, String guestId, BigDecimal amount) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", LocalDate.now().plusDays(220).toString());
        payload.put("checkOutDate", LocalDate.now().plusDays(222).toString());
        payload.put("totalAmount", amount);
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
