package com.guest_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.PaymentRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.payments.stripe.mode=live",
        "app.payments.stripe.secret-key=stripe-test-key-not-used-by-webhook-tests",
        "app.payments.stripe.webhook-secret=stripe-test-webhook-secret" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StripeLiveWebhookIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";
    private static final String WEBHOOK_SECRET = "stripe-test-webhook-secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void verifiedCheckoutSuccessCompletesTheSharedPaymentFlowExactlyOnce() throws Exception {
        String hostToken = register("stripe-live-success@example.com", "Stripe Live Host");
        String propertyId = createProperty(hostToken, "Stripe Live Property");
        String guestId = createGuest(hostToken, "Stripe Live Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, "PENDING_PAYMENT");
        String guestToken = createGuestLink(hostToken, bookingId);
        Payment payment = createStripePayment(bookingId, "cs_test_hostvero_success");
        String payload = checkoutEvent("evt_hostvero_success", "checkout.session.completed", payment, bookingId,
                "cs_test_hostvero_success", "paid", 45000L);

        mockMvc.perform(post("/api/webhooks/stripe").header("Stripe-Signature", stripeSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payments/{paymentId}", payment.getId()).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/public/guest/{token}", guestToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("STAY_ACTIVE"));
        mockMvc.perform(post("/api/webhooks/stripe").header("Stripe-Signature", stripeSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/receipts").header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidSignatureIsRejectedAndVerifiedFailedPaymentDoesNotConfirmBooking() throws Exception {
        mockMvc.perform(post("/api/webhooks/stripe").header("Stripe-Signature", "t=1,v1=invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        String hostToken = register("stripe-live-failed@example.com", "Stripe Failure Host");
        String propertyId = createProperty(hostToken, "Stripe Failure Property");
        String guestId = createGuest(hostToken, "Stripe Failure Guest");
        String bookingId = createBooking(hostToken, propertyId, guestId, "PENDING_PAYMENT");
        Payment payment = createStripePayment(bookingId, "cs_test_hostvero_failure");
        String payload = paymentIntentEvent("evt_hostvero_failed", "payment_intent.payment_failed", payment, bookingId,
                "Insufficient funds");

        mockMvc.perform(post("/api/webhooks/stripe").header("Stripe-Signature", stripeSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/payments/{paymentId}", payment.getId()).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        mockMvc.perform(get("/api/bookings/{bookingId}/receipt", bookingId).header("Authorization", bearer(hostToken)))
                .andExpect(status().isNotFound());
    }

    private Payment createStripePayment(String bookingId, String providerReference) {
        return transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
            return paymentRepository.saveAndFlush(new Payment(booking.getHost(), booking, PaymentProvider.STRIPE,
                    providerReference, booking.getTotalAmount(), booking.getCurrency()));
        });
    }

    private String checkoutEvent(String eventId, String type, Payment payment, String bookingId, String sessionId,
            String paymentStatus, long amountTotal) throws Exception {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", sessionId);
        session.put("payment_status", paymentStatus);
        session.put("amount_total", amountTotal);
        session.put("currency", "kes");
        session.put("metadata", Map.of("paymentId", payment.getId().toString(), "bookingId", bookingId));
        return objectMapper.writeValueAsString(Map.of("id", eventId, "object", "event", "type", type,
                "data", Map.of("object", session)));
    }

    private String paymentIntentEvent(String eventId, String type, Payment payment, String bookingId, String reason)
            throws Exception {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("id", "pi_test_hostvero_failure");
        intent.put("currency", "kes");
        intent.put("metadata", Map.of("paymentId", payment.getId().toString(), "bookingId", bookingId));
        intent.put("last_payment_error", Map.of("message", reason));
        return objectMapper.writeValueAsString(Map.of("id", eventId, "object", "event", "type", type,
                "data", Map.of("object", intent)));
    }

    private String stripeSignature(String payload) throws Exception {
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload)
                .getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
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

    private String createBooking(String token, String propertyId, String guestId, String bookingStatus) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", propertyId);
        payload.put("guestId", guestId);
        payload.put("checkInDate", LocalDate.now().plusDays(150).toString());
        payload.put("checkOutDate", LocalDate.now().plusDays(152).toString());
        payload.put("totalAmount", new BigDecimal("450.00"));
        payload.put("currency", "KES");
        payload.put("status", bookingStatus);
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
