package com.guest_platform;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseFourIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";
    private static final String MPESA_WEBHOOK_SECRET = "phase4-test-mpesa-webhook-secret";
    private static final String STRIPE_WEBHOOK_SECRET = "phase4-test-stripe-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void hostCanInitiateOwnPaymentUsingBookingAmountAndCurrencyOnly() throws Exception {
        String ownerToken = register("phase4-init-owner@example.com", "Payment Owner");
        String propertyId = createProperty(ownerToken, "Payment Property");
        String guestId = createGuest(ownerToken, "Payment Guest");
        String bookingId = createBooking(ownerToken, propertyId, guestId, LocalDate.now().plusDays(70),
                LocalDate.now().plusDays(72), "PENDING_PAYMENT", new BigDecimal("321.45"));

        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MPESA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("MPESA"))
                .andExpect(jsonPath("$.amount").value(321.45))
                .andExpect(jsonPath("$.currency").value("KES"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andReturn();
        String paymentId = json(result).get("id").asText();

        mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MPESA\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(321.45));

        String otherToken = register("phase4-init-other@example.com", "Other Host");
        mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"STRIPE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments/{paymentId}", paymentId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());

        String pendingConfirmationBookingId = createBooking(ownerToken, propertyId, guestId, LocalDate.now().plusDays(75),
                LocalDate.now().plusDays(77), "PENDING_CONFIRMATION", new BigDecimal("200.00"));
        mockMvc.perform(post("/api/bookings/{bookingId}/payments", pendingConfirmationBookingId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MPESA\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"UNSUPPORTED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifiedStripeSuccessConfirmsBookingCreatesOneReceiptAndSupportsValidGuestLinkOnly() throws Exception {
        String ownerToken = register("phase4-stripe-owner@example.com", "Stripe Owner");
        String propertyId = createProperty(ownerToken, "Stripe Property");
        String guestId = createGuest(ownerToken, "Stripe Guest");
        String bookingId = createBooking(ownerToken, propertyId, null, LocalDate.now().plusDays(80),
                LocalDate.now().plusDays(82), "PENDING_PAYMENT", new BigDecimal("450.00"));
        String guestToken = createGuestLink(ownerToken, bookingId);
        mockMvc.perform(put("/api/public/guest/{token}/registration", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Stripe Guest\",\"phone\":\"+254722333444\",\"email\":\"stripe.guest@example.com\"}"))
                .andExpect(status().isNoContent());
        String paymentId = initiate(ownerToken, bookingId, "STRIPE");
        String providerReference = payment(ownerToken, paymentId).get("providerReference").asText();
        String payload = webhookPayload(providerReference, "stripe-event-001", true, null);

        mockMvc.perform(post("/api/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        mockMvc.perform(get("/api/bookings/{bookingId}/receipt", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/webhooks/stripe")
                        .header("Stripe-Signature", stripeSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        MvcResult receiptResult = mockMvc.perform(get("/api/bookings/{bookingId}/receipt", bookingId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value(org.hamcrest.Matchers.startsWith("HV-")))
                .andReturn();
        String receiptId = json(receiptResult).get("id").asText();
        String receiptNumber = json(receiptResult).get("receiptNumber").asText();

        mockMvc.perform(get("/api/bookings/{bookingId}/receipt/document", bookingId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hostvero")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stripe Property")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stripe Guest")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(receiptNumber)));
        mockMvc.perform(get("/api/bookings/{bookingId}/receipt/document", bookingId)
                        .queryParam("download", "true").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        mockMvc.perform(delete("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/webhooks/stripe")
                        .header("Stripe-Signature", stripeSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/receipts").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/public/guest/{token}/receipt", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.bookingId").doesNotExist())
                .andExpect(jsonPath("$.receiptNumber").exists());

        String otherToken = register("phase4-stripe-other@example.com", "Receipt Other Host");
        mockMvc.perform(get("/api/receipts/{receiptId}", receiptId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/{bookingId}/receipt/document", bookingId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/public/guest/{token}/receipt", guestToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guest/{token}/receipt/document", guestToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(receiptNumber)));
        mockMvc.perform(get("/api/public/guest/{token}/receipt", "not-a-valid-token")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/guest/{token}/receipt/document", "not-a-valid-token"))
                .andExpect(status().isNotFound());

        String secondBookingId = createBooking(ownerToken, propertyId, guestId, LocalDate.now().plusDays(85),
                LocalDate.now().plusDays(87), "PENDING_PAYMENT", new BigDecimal("451.00"));
        String secondPaymentId = initiate(ownerToken, secondBookingId, "MPESA");
        String secondReference = payment(ownerToken, secondPaymentId).get("providerReference").asText();
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(secondReference, "mpesa-event-second-success", true, null)))
                .andExpect(status().isNoContent());
        MvcResult secondReceiptResult = mockMvc.perform(get("/api/bookings/{bookingId}/receipt", secondBookingId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn();
        assertNotEquals(receiptNumber, json(secondReceiptResult).get("receiptNumber").asText());

        String expiredBookingId = createBooking(ownerToken, propertyId, guestId, LocalDate.now().minusDays(8),
                LocalDate.now().minusDays(6), "PENDING_PAYMENT", new BigDecimal("452.00"));
        String expiredToken = createGuestLink(ownerToken, expiredBookingId);
        String expiredPaymentId = initiate(ownerToken, expiredBookingId, "MPESA");
        String expiredReference = payment(ownerToken, expiredPaymentId).get("providerReference").asText();
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(expiredReference, "mpesa-event-expired-link", true, null)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/guest/{token}/receipt", expiredToken)).andExpect(status().isNotFound());
    }

    @Test
    void failedPaymentsDoNotConfirmAndLateSuccessDoesNotResurrectCancelledBooking() throws Exception {
        String token = register("phase4-failed-owner@example.com", "Failure Owner");
        String propertyId = createProperty(token, "Failure Property");
        String guestId = createGuest(token, "Failure Guest");
        String failedBookingId = createBooking(token, propertyId, guestId, LocalDate.now().plusDays(90),
                LocalDate.now().plusDays(92), "PENDING_PAYMENT", new BigDecimal("100.00"));
        String failedPaymentId = initiate(token, failedBookingId, "MPESA");
        String failedReference = payment(token, failedPaymentId).get("providerReference").asText();
        String failedPayload = webhookPayload(failedReference, "mpesa-event-failed", false, "Insufficient funds");
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(failedPayload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payments/{paymentId}", failedPaymentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(get("/api/bookings/{bookingId}", failedBookingId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        mockMvc.perform(get("/api/bookings/{bookingId}/receipt", failedBookingId).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        String cancelledBookingId = createBooking(token, propertyId, guestId, LocalDate.now().plusDays(95),
                LocalDate.now().plusDays(97), "PENDING_PAYMENT", new BigDecimal("125.00"));
        String cancelledPaymentId = initiate(token, cancelledBookingId, "MPESA");
        String cancelledReference = payment(token, cancelledPaymentId).get("providerReference").asText();
        mockMvc.perform(delete("/api/bookings/{bookingId}", cancelledBookingId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/webhooks/mpesa")
                        .header("X-Mpesa-Webhook-Secret", MPESA_WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload(cancelledReference, "mpesa-event-late-success", true, null)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bookings/{bookingId}", cancelledBookingId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/payments/{paymentId}", cancelledPaymentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void cancellingAnUnpaidBookingRevokesItsGuestLinkAcrossPublicEndpoints() throws Exception {
        String token = register("phase4-cancel-owner@example.com", "Cancel Owner");
        String propertyId = createProperty(token, "Cancellation Property");
        String guestId = createGuest(token, "Cancellation Guest");
        String bookingId = createBooking(token, propertyId, guestId, LocalDate.now().plusDays(105),
                LocalDate.now().plusDays(107), "PENDING_PAYMENT", new BigDecimal("220.00"));
        String guestToken = createGuestLink(token, bookingId);

        mockMvc.perform(delete("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/public/guest/{token}", guestToken)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/public/guest/{token}/payments", guestToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"STRIPE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/public/guest/{token}/email-verification/confirm", guestToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/guest/{token}/receipt", guestToken)).andExpect(status().isNotFound());
    }

    @Test
    void hostCanConfirmCashPaymentExactlyOnceAndPaidBookingsCannotBeCancelled() throws Exception {
        String ownerToken = register("phase4-cash-owner@example.com", "Cash Owner");
        String otherToken = register("phase4-cash-other@example.com", "Cash Other Host");
        String propertyId = createProperty(ownerToken, "Cash Property");
        String guestId = createGuest(ownerToken, "Cash Guest");
        String bookingId = createBooking(ownerToken, propertyId, guestId, LocalDate.now().plusDays(110),
                LocalDate.now().plusDays(112), "PENDING_PAYMENT", new BigDecimal("375.00"));
        String guestToken = createGuestLink(ownerToken, bookingId);

        mockMvc.perform(post("/api/bookings/{bookingId}/payments/cash/confirm", bookingId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bookings/{bookingId}/payments/cash/confirm", bookingId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("CASH"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount").value(375.00))
                .andExpect(jsonPath("$.currency").value("KES"));
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/bookings/{bookingId}/receipt", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("STAY_ACTIVE"));

        mockMvc.perform(post("/api/bookings/{bookingId}/payments/cash/confirm", bookingId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict());
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
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(propertyPayload(name))))
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

    private String createBooking(String token, String propertyId, String guestId, LocalDate checkIn, LocalDate checkOut,
            String statusValue, BigDecimal amount) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", checkIn.toString());
        payload.put("checkOutDate", checkOut.toString());
        payload.put("totalAmount", amount);
        payload.put("currency", "KES");
        payload.put("status", statusValue);
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

    private String initiate(String token, String bookingId, String provider) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings/{bookingId}/payments", bookingId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"" + provider + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    private JsonNode payment(String token, String paymentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn();
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

    private String stripeSignature(String payload) throws Exception {
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(STRIPE_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload)
                .getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
