package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.entity.HostPayoutStatus;
import com.guest_platform.repository.HostPayoutRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaystackIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";
    private static final String PAYSTACK_SECRET = "phase10-test-paystack-secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HostPayoutRepository hostPayoutRepository;
    private int bookingOffset;

    @Test
    void initiationUsesServerCalculatedFivePercentFeeAndNeverClientAmounts() throws Exception {
        String hostToken = register("paystack-init@example.com", "Paystack Host");
        configurePayout(hostToken);
        String propertyId = createProperty(hostToken, "Paystack Property");
        String guestId = createGuest(hostToken, "Paystack Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, new BigDecimal("3500.00"));

        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(hostToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"PAYSTACK\",\"amount\":1,\"serviceFee\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("PAYSTACK"))
                .andExpect(jsonPath("$.bookingAmount").value(3500.00))
                .andExpect(jsonPath("$.serviceFee").value(175.00))
                .andExpect(jsonPath("$.chargedAmount").value(3675.00))
                .andExpect(jsonPath("$.amount").value(3675.00))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andReturn();
        String paymentId = json(result).get("id").asText();
        mockMvc.perform(get("/api/payments/{paymentId}", paymentId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serviceFee").value(175.00));
    }

    @Test
    void verifiedPaystackWebhookCompletesOnceAndRejectsInvalidOrMismatchedEvents() throws Exception {
        String hostToken = register("paystack-webhook@example.com", "Webhook Host");
        configurePayout(hostToken);
        String propertyId = createProperty(hostToken, "Webhook Property");
        String guestId = createGuest(hostToken, "Webhook Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, new BigDecimal("3500.00"));
        String guestToken = createGuestLink(hostToken, bookingId);
        JsonNode payment = initiate(hostToken, bookingId);
        String payload = successPayload(payment, bookingId, 367500L, "KES", 1001L);

        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payments/{paymentId}", payment.get("id").asText())
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/payments/{paymentId}", payment.get("id").asText())
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processorFee").value(55.00))
                .andExpect(jsonPath("$.hostPayoutAmount").value(3500.00))
                .andExpect(jsonPath("$.hostveroNetAmount").value(120.00));
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("STAY_ACTIVE"));
        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/receipts").header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"STRIPE\"}"))
                .andExpect(status().isConflict());

        String mismatchBookingId = createBooking(hostToken, propertyId, guestId, new BigDecimal("3500.00"));
        JsonNode mismatchPayment = initiate(hostToken, mismatchBookingId);
        String wrongAmount = successPayload(mismatchPayment, mismatchBookingId, 1L, "KES", 1002L);
        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(wrongAmount))
                        .contentType(MediaType.APPLICATION_JSON).content(wrongAmount))
                .andExpect(status().isConflict());
        String wrongCurrency = successPayload(mismatchPayment, mismatchBookingId, 367500L, "USD", 1003L);
        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(wrongCurrency))
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCurrency))
                .andExpect(status().isConflict());
        Map<String, Object> unknownData = new LinkedHashMap<>();
        unknownData.put("id", 1005L);
        unknownData.put("status", "success");
        unknownData.put("reference", "PAYSTACK-UNKNOWN");
        unknownData.put("amount", 367500L);
        unknownData.put("currency", "KES");
        unknownData.put("metadata", Map.of("paymentId", mismatchPayment.get("id").asText(), "bookingId", mismatchBookingId));
        String unknownReference = objectMapper.writeValueAsString(Map.of("event", "charge.success", "data", unknownData));
        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(unknownReference))
                        .contentType(MediaType.APPLICATION_JSON).content(unknownReference))
                .andExpect(status().isNotFound());
    }

    @Test
    void latePaystackSuccessAfterCancellationCannotRestoreBookingOrGuestLink() throws Exception {
        String hostToken = register("paystack-cancel@example.com", "Cancellation Host");
        configurePayout(hostToken);
        String propertyId = createProperty(hostToken, "Cancellation Property");
        String guestId = createGuest(hostToken, "Cancellation Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, new BigDecimal("3500.00"));
        String guestToken = createGuestLink(hostToken, bookingId);
        JsonNode payment = initiate(hostToken, bookingId);

        mockMvc.perform(delete("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isNoContent());
        String payload = successPayload(payment, bookingId, 367500L, "KES", 1004L);
        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/public/guest/{token}", guestToken)).andExpect(status().isNotFound());
    }

    @Test
    void cashRemainsFeeFreeAndBlocksLaterPaystackInitiation() throws Exception {
        String hostToken = register("paystack-cash@example.com", "Cash Host");
        String propertyId = createProperty(hostToken, "Cash Property");
        String guestId = createGuest(hostToken, "Cash Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, new BigDecimal("3500.00"));

        mockMvc.perform(post("/api/bookings/{bookingId}/payments/cash/confirm", bookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("CASH"))
                .andExpect(jsonPath("$.bookingAmount").value(3500.00))
                .andExpect(jsonPath("$.serviceFee").value(0))
                .andExpect(jsonPath("$.chargedAmount").value(3500.00));
        mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId).header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"PAYSTACK\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void processorFeeAboveServiceFeeIsRecordedAsNegativePlatformNetWithoutReducingHostPayout() throws Exception {
        String hostToken = register("paystack-negative-net@example.com", "Negative Net Host");
        configurePayout(hostToken);
        String propertyId = createProperty(hostToken, "Negative Net Property");
        String guestId = createGuest(hostToken, "Negative Net Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, new BigDecimal("3500.00"));
        JsonNode payment = initiate(hostToken, bookingId);
        String payload = successPayload(payment, bookingId, 367500L, "KES", 1006L, 20000L);

        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payments/{paymentId}", payment.get("id").asText())
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processorFee").value(200.00))
                .andExpect(jsonPath("$.hostPayoutAmount").value(3500.00))
                .andExpect(jsonPath("$.hostveroNetAmount").value(-25.00));
    }

    @Test
    void mpesaPayoutHostUsesRecipientLifecycleAndDuplicateWebhookCreatesOnePendingPayout() throws Exception {
        String hostToken = register("paystack-mpesa@example.com", "M-Pesa Settlement Host");
        configureMpesaPayout(hostToken);
        String propertyId = createProperty(hostToken, "M-Pesa Settlement Property");
        String guestId = createGuest(hostToken, "M-Pesa Settlement Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, new BigDecimal("3500.00"));
        JsonNode payment = initiate(hostToken, bookingId);
        String payload = successPayload(payment, bookingId, 367500L, "KES", 1007L);

        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        var payout = hostPayoutRepository.findByPaymentId(java.util.UUID.fromString(payment.get("id").asText()))
                .orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(HostPayoutStatus.PENDING);
        assertThat(payout.getAmount()).isEqualByComparingTo("3500.00");
        assertThat(payout.getCurrency()).isEqualTo("KES");
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/webhooks/paystack").header("x-paystack-signature", paystackSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        assertThat(hostPayoutRepository.findByPaymentId(java.util.UUID.fromString(payment.get("id").asText())))
                .isPresent();
    }

    private JsonNode initiate(String token, String bookingId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"PAYSTACK\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(result);
    }

    private String successPayload(JsonNode payment, String bookingId, long amount, String currency, long transactionId)
            throws Exception {
        return successPayload(payment, bookingId, amount, currency, transactionId, 5500L);
    }

    private String successPayload(JsonNode payment, String bookingId, long amount, String currency, long transactionId,
            long processorFeeMinor) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", transactionId);
        data.put("status", "success");
        data.put("reference", payment.get("providerReference").asText());
        data.put("amount", amount);
        data.put("currency", currency);
        data.put("fees", processorFeeMinor);
        data.put("metadata", Map.of("paymentId", payment.get("id").asText(), "bookingId", bookingId));
        return objectMapper.writeValueAsString(Map.of("event", "charge.success", "data", data));
    }

    private String paystackSignature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(PAYSTACK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private void configurePayout(String token) throws Exception {
        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "payoutMethod", "BANK_ACCOUNT", "settlementBankCode", "KEPSS-TEST",
                                "accountNumber", "0123456789", "accountName", "Paystack Host Account"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
    }

    private void configureMpesaPayout(String token) throws Exception {
        mockMvc.perform(put("/api/me/payout-settings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "payoutMethod", "MPESA", "mpesaPhone", "+254712345678"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.payoutMethod").value("MPESA"));
    }

    private String register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "passwordConfirmation", PASSWORD, "fullName", fullName, "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("accessToken").asText();
    }

    private String createProperty(String token, String name) throws Exception {
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
        MvcResult result = mockMvc.perform(post("/api/properties").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String createGuest(String token, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/guests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "fullName", fullName, "phone", "+254722333444",
                                "email", fullName.toLowerCase().replace(' ', '.') + "@example.com"))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private String createBooking(String token, String propertyId, String guestId, BigDecimal amount) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        LocalDate checkIn = LocalDate.now().plusDays(190 + bookingOffset * 3L);
        bookingOffset++;
        payload.put("checkInDate", checkIn.toString());
        payload.put("checkOutDate", checkIn.plusDays(2).toString());
        payload.put("totalAmount", amount);
        payload.put("currency", "KES");
        payload.put("status", "PENDING_PAYMENT");
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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
