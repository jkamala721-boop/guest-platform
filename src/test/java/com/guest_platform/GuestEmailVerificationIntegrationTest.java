package com.guest_platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.entity.Notification;
import com.guest_platform.entity.NotificationChannel;
import com.guest_platform.entity.NotificationType;
import com.guest_platform.repository.GuestRepository;
import com.guest_platform.repository.NotificationRepository;
import com.guest_platform.service.notification.NotificationProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.guest-email-verification.code-ttl-seconds=2",
        "app.guest-email-verification.resend-cooldown-seconds=1",
        "app.guest-email-verification.maximum-attempts=5" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GuestEmailVerificationIntegrationTest.EmailProviderConfiguration.class)
class GuestEmailVerificationIntegrationTest {

    private static final String PASSWORD = "StrongPass!123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordingEmailProvider emailProvider;
    @Autowired private GuestRepository guestRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void verificationCodeIsHashedAndCorrectCodeVerifiesEmailThenAllowsOperationalEmail() throws Exception {
        Session session = registeredGuest("verify-correct@example.com");
        String code = requestCode(session);

        Notification verification = notificationRepository.findByBookingIdAndType(UUID.fromString(session.bookingId()),
                NotificationType.EMAIL_VERIFICATION).orElseThrow();
        var guest = guestRepository.findByIdAndHostId(verification.getGuest().getId(), verification.getHost().getId())
                .orElseThrow();
        assertNotEquals(code, guest.getEmailVerificationCodeHash());
        assertTrue(passwordEncoder.matches(code, guest.getEmailVerificationCodeHash()));
        assertFalse(verification.getMessage().contains(code));

        mockMvc.perform(get("/api/public/guest/{token}", session.guestToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerificationCodeHash").doesNotExist())
                .andExpect(jsonPath("$.emailVerified").value(false));

        mockMvc.perform(post("/api/bookings/{bookingId}/notifications/manual", session.bookingId())
                        .header("Authorization", bearer(session.hostToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"subject\":\"Welcome\",\"message\":\"Hello\"}"))
                .andExpect(status().isConflict());

        confirm(session.guestToken(), code).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        mockMvc.perform(post("/api/bookings/{bookingId}/notifications/manual", session.bookingId())
                        .header("Authorization", bearer(session.hostToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"subject\":\"Welcome\",\"message\":\"Hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void wrongAndExpiredCodesAreRejected() throws Exception {
        Session session = registeredGuest("verify-failure@example.com");
        String code = requestCode(session);
        String wrongCode = "000000".equals(code) ? "000001" : "000000";

        confirm(session.guestToken(), wrongCode).andExpect(status().isConflict());
        Thread.sleep(2_100);
        confirm(session.guestToken(), code).andExpect(status().isConflict());
    }

    @Test
    void resendInvalidatesPriorChallengeAndUsesCooldown() throws Exception {
        Session session = registeredGuest("verify-resend@example.com");
        String firstCode = requestCode(session);
        mockMvc.perform(post("/api/public/guest/{token}/email-verification", session.guestToken()))
                .andExpect(status().isConflict());

        Thread.sleep(2_100);
        String secondCode = requestCode(session);
        assertNotEquals(firstCode, secondCode);
        confirm(session.guestToken(), firstCode).andExpect(status().isConflict());
        confirm(session.guestToken(), secondCode).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void changingEmailResetsVerificationAndAnotherGuestLinkCannotUseTheCode() throws Exception {
        Session first = registeredGuest("verify-change@example.com");
        String code = requestCode(first);
        confirm(first.guestToken(), code).andExpect(status().isOk());

        mockMvc.perform(put("/api/public/guest/{token}/registration", first.guestToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("changed@example.com")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/guest/{token}", first.guestToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));

        Session other = registeredGuest("verify-other@example.com");
        String otherCode = requestCode(other);
        confirm(first.guestToken(), otherCode).andExpect(status().isConflict());
        confirm(other.guestToken(), otherCode).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
    }

    private String requestCode(Session session) throws Exception {
        mockMvc.perform(post("/api/public/guest/{token}/email-verification", session.guestToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.resendAvailableAt").exists());
        return emailProvider.codeFor(UUID.fromString(session.bookingId()));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String code) throws Exception {
        return mockMvc.perform(post("/api/public/guest/{token}/email-verification/confirm", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private Session registeredGuest(String email) throws Exception {
        String hostToken = register(email.replace("@", ".host@"));
        String propertyId = createProperty(hostToken);
        String bookingId = createBooking(hostToken, propertyId);
        String guestToken = json(mockMvc.perform(post("/api/bookings/{bookingId}/guest-link", bookingId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
        mockMvc.perform(put("/api/public/guest/{token}/registration", guestToken)
                        .contentType(MediaType.APPLICATION_JSON).content(registrationPayload(email)))
                .andExpect(status().isNoContent());
        return new Session(hostToken, bookingId, guestToken);
    }

    private String register(String email) throws Exception {
        return json(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "passwordConfirmation", PASSWORD, "fullName", "Verification Host", "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn()).get("accessToken").asText();
    }

    private String createProperty(String hostToken) throws Exception {
        return json(mockMvc.perform(post("/api/properties").header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("name", "Verification Property",
                                "propertyType", "APARTMENT", "address", "1 Test Street", "mapsUrl", "https://maps.google.com/?q=test",
                                "maxGuests", 2, "defaultNightlyRate", new BigDecimal("100"), "currency", "KES",
                                "checkInTime", LocalTime.of(14, 0).toString(), "checkOutTime", LocalTime.of(10, 0).toString(), "active", true))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String createBooking(String hostToken, String propertyId) throws Exception {
        return json(mockMvc.perform(post("/api/bookings").header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("propertyId", propertyId,
                                "checkInDate", LocalDate.now().plusDays(14).toString(), "checkOutDate", LocalDate.now().plusDays(16).toString(),
                                "totalAmount", new BigDecimal("200"), "currency", "KES", "status", "PENDING_PAYMENT"))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String registrationPayload(String email) {
        return "{\"fullName\":\"Verification Guest\",\"phone\":\"+254722333444\",\"email\":\"" + email + "\"}";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(String hostToken, String bookingId, String guestToken) {
    }

    @TestConfiguration
    static class EmailProviderConfiguration {
        @Bean
        @Primary
        RecordingEmailProvider recordingEmailProvider() {
            return new RecordingEmailProvider();
        }
    }

    static class RecordingEmailProvider implements NotificationProvider {
        private final Map<UUID, String> codes = new ConcurrentHashMap<>();

        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public void deliver(Notification notification) {
            if (notification.getType() == NotificationType.EMAIL_VERIFICATION) {
                codes.put(notification.getBooking().getId(), notification.getDeliveryParameters().getFirst());
            }
        }

        String codeFor(UUID bookingId) {
            String code = codes.get(bookingId);
            if (code == null) {
                throw new AssertionError("Verification code was not delivered to the fake provider");
            }
            return code;
        }
    }
}
