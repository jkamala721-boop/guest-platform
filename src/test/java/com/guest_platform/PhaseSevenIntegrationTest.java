package com.guest_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class PhaseSevenIntegrationTest {
    private static final String PASSWORD="StrongPass!123", WEBHOOK="phase4-test-mpesa-webhook-secret";
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @Test void pendingPaymentBookingCannotExtendWithoutASeparateVerifiedPayment() throws Exception {
        String token=register("phase7-unpaid@example.com"); String property=property(token); String guest=guest(token);
        LocalDate in=LocalDate.now().plusDays(40); String booking=booking(token,property,guest,in,in.plusDays(2),"PENDING_PAYMENT");
        mvc.perform(post("/api/bookings/{id}/extend",booking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(4)+"\"}"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/bookings/{id}",booking).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.checkOutDate").value(in.plusDays(2).toString())).andExpect(jsonPath("$.totalAmount").value(450.00));
    }

    @Test void paidExtensionAppliesOnlyAfterOneVerifiedAdditionalPayment() throws Exception {
        String token=register("phase7-paid@example.com"); String property=property(token); String guest=guest(token); LocalDate in=LocalDate.now().plusDays(50);
        String booking=booking(token,property,guest,in,in.plusDays(2),"PENDING_PAYMENT"); paySuccess(token,booking,"phase7-original");
        MvcResult extension=mvc.perform(post("/api/bookings/{id}/extend",booking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(4)+"\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT")).andExpect(jsonPath("$.additionalAmount").value(240.00)).andReturn();
        String extensionId=body(extension).get("id").asText();
        mvc.perform(get("/api/bookings/{id}",booking).header("Authorization",bearer(token))).andExpect(jsonPath("$.checkOutDate").value(in.plusDays(2).toString()));
        MvcResult payment=mvc.perform(post("/api/booking-extensions/{id}/payments",extensionId).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MPESA\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(240.00)).andReturn();
        String reference=body(payment).get("providerReference").asText(); webhook(reference,"phase7-extension",true);
        mvc.perform(get("/api/bookings/{id}",booking).header("Authorization",bearer(token))).andExpect(jsonPath("$.checkOutDate").value(in.plusDays(4).toString())).andExpect(jsonPath("$.totalAmount").value(690.00));
        webhook(reference,"phase7-extension",true);
        mvc.perform(get("/api/bookings/{id}",booking).header("Authorization",bearer(token))).andExpect(jsonPath("$.totalAmount").value(690.00));
    }

    @Test void bookAgainAndGuestAvailabilityAreSafeAndTenantScoped() throws Exception {
        String owner=register("phase7-owner@example.com"), property=property(owner), guest=guest(owner); LocalDate in=LocalDate.now().plusDays(70);
        String booking=booking(owner,property,guest,in,in.plusDays(2),"PENDING_PAYMENT"); String link=guestLink(owner,booking);
        String registeredGuest=body(mvc.perform(get("/api/bookings/{id}",booking).header("Authorization",bearer(owner))).andExpect(status().isOk()).andReturn()).get("guestId").asText();
        MvcResult repeat=mvc.perform(post("/api/bookings/{id}/book-again",booking).header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"checkInDate\":\""+in.plusDays(10)+"\",\"checkOutDate\":\""+in.plusDays(13)+"\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.booking.guestId").value(registeredGuest)).andExpect(jsonPath("$.guestLink.token").exists()).andReturn();
        String newBooking=body(repeat).get("booking").get("id").asText();
        mvc.perform(get("/api/bookings/{id}",booking).header("Authorization",bearer(owner))).andExpect(jsonPath("$.checkOutDate").value(in.plusDays(2).toString()));
        mvc.perform(get("/api/public/guest/{token}/availability",link).param("from",in.toString()).param("to",in.plusDays(5).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.propertyId").value(property)).andExpect(jsonPath("$.unavailableRanges[0].checkInDate").value(in.toString())).andExpect(jsonPath("$.bookingId").doesNotExist());
        String other=register("phase7-other@example.com"); mvc.perform(post("/api/bookings/{id}/book-again",booking).header("Authorization",bearer(other)).contentType(MediaType.APPLICATION_JSON).content("{\"checkInDate\":\""+in.plusDays(20)+"\",\"checkOutDate\":\""+in.plusDays(22)+"\"}")) .andExpect(status().isNotFound());
        mvc.perform(post("/api/public/guest/not-valid/extend").contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(3)+"\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/{id}",newBooking).header("Authorization",bearer(owner))).andExpect(status().isOk());
    }

    @Test void bookAgainRejectsCancelledSourcesInactivePropertiesAndArchivedGuests() throws Exception {
        String token=register("phase7-book-again-safety@example.com"); LocalDate in=LocalDate.now().plusDays(75);

        String cancelledProperty=property(token), cancelledGuest=guest(token);
        String cancelledBooking=booking(token,cancelledProperty,cancelledGuest,in,in.plusDays(2),"PENDING_PAYMENT");
        mvc.perform(delete("/api/bookings/{id}",cancelledBooking).header("Authorization",bearer(token))).andExpect(status().isNoContent());
        mvc.perform(post("/api/bookings/{id}/book-again",cancelledBooking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"checkInDate\":\""+in.plusDays(10)+"\",\"checkOutDate\":\""+in.plusDays(12)+"\"}"))
            .andExpect(status().isConflict());

        String inactiveProperty=property(token), inactiveGuest=guest(token);
        String inactiveBooking=booking(token,inactiveProperty,inactiveGuest,in.plusDays(20),in.plusDays(22),"PENDING_PAYMENT");
        mvc.perform(delete("/api/properties/{id}",inactiveProperty).header("Authorization",bearer(token))).andExpect(status().isNoContent());
        mvc.perform(post("/api/bookings/{id}/book-again",inactiveBooking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"checkInDate\":\""+in.plusDays(30)+"\",\"checkOutDate\":\""+in.plusDays(32)+"\"}"))
            .andExpect(status().isNotFound());

        String archivedProperty=property(token), archivedGuest=guest(token);
        String archivedBooking=booking(token,archivedProperty,archivedGuest,in.plusDays(40),in.plusDays(42),"PENDING_PAYMENT");
        mvc.perform(delete("/api/guests/{id}",archivedGuest).header("Authorization",bearer(token))).andExpect(status().isNoContent());
        mvc.perform(post("/api/bookings/{id}/book-again",archivedBooking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"checkInDate\":\""+in.plusDays(50)+"\",\"checkOutDate\":\""+in.plusDays(52)+"\"}"))
            .andExpect(status().isConflict());
    }

    @Test void extensionsRejectConflictsButAllowBackToBackDatesAndStayHostScoped() throws Exception {
        String owner=register("phase7-conflicts@example.com"), property=property(owner), guest=guest(owner); LocalDate in=LocalDate.now().plusDays(80);
        String booking=booking(owner,property,guest,in,in.plusDays(2),"PENDING_PAYMENT");
        paySuccess(owner,booking,"phase7-conflict-original");
        booking(owner,property,guest,in.plusDays(4),in.plusDays(6),"PENDING_PAYMENT");
        mvc.perform(post("/api/bookings/{id}/extend",booking).header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(4)+"\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings/{id}/extend",booking).header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(5)+"\"}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/{id}/extend",booking).header("Authorization",bearer(register("phase7-conflict-other@example.com"))).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(5)+"\"}"))
            .andExpect(status().isNotFound());
    }

    @Test void failedExtensionPaymentLeavesTheConfirmedBookingUntouched() throws Exception {
        String token=register("phase7-failed-extension@example.com"), property=property(token), guest=guest(token); LocalDate in=LocalDate.now().plusDays(90);
        String booking=booking(token,property,guest,in,in.plusDays(2),"PENDING_PAYMENT"); paySuccess(token,booking,"phase7-failed-original");
        String extensionId=body(mvc.perform(post("/api/bookings/{id}/extend",booking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(4)+"\"}"))
            .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String reference=body(mvc.perform(post("/api/booking-extensions/{id}/payments",extensionId).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MPESA\"}"))
            .andExpect(status().isCreated()).andReturn()).get("providerReference").asText();
        webhook(reference,"phase7-extension-failed",false);
        mvc.perform(get("/api/bookings/{id}",booking).header("Authorization",bearer(token))).andExpect(jsonPath("$.checkOutDate").value(in.plusDays(2).toString())).andExpect(jsonPath("$.totalAmount").value(450.00));
    }

    @Test void calendarAndPublicLinksRespectAvailabilityAndSafeLinkStates() throws Exception {
        String token=register("phase7-links@example.com"), property=property(token), guest=guest(token); LocalDate in=LocalDate.now().plusDays(100);
        String booking=booking(token,property,guest,in,in.plusDays(2),"PENDING_PAYMENT"); String oldLink=guestLink(token,booking); String activeLink=guestLink(token,booking);
        mvc.perform(get("/api/properties/{id}/availability",property).header("Authorization",bearer(token)).param("from",in.toString()).param("to",in.plusDays(3).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unavailableRanges[0].checkOutDate").value(in.plusDays(2).toString()));
        mvc.perform(get("/api/properties/{id}/availability",property).param("from",in.toString()).param("to",in.plusDays(3).toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/public/guest/{token}/book-again",oldLink).contentType(MediaType.APPLICATION_JSON).content("{\"checkInDate\":\""+in.plusDays(10)+"\",\"checkOutDate\":\""+in.plusDays(12)+"\"}"))
            .andExpect(status().isNotFound());
        jdbc.update("update guest_links set expires_at = ? where token_hash = ?", Instant.now().minusSeconds(60), hash(activeLink));
        mvc.perform(post("/api/public/guest/{token}/extend",activeLink).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(3)+"\"}"))
            .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertNotEquals(activeLink, jdbc.queryForObject("select token_hash from guest_links where token_hash = ?", String.class, hash(activeLink)));
    }

    @Test void pendingExtensionReconciliationDoesNotDuplicateNotifications() throws Exception {
        String token=register("phase7-notifications@example.com"), property=property(token), guest=guest(token); LocalDate in=LocalDate.now().plusDays(110);
        String booking=booking(token,property,guest,in,in.plusDays(2),"PENDING_PAYMENT"); paySuccess(token,booking,"phase7-notifications-original"); Long before=jdbc.queryForObject("select count(*) from notifications where booking_id = ?",Long.class,java.util.UUID.fromString(booking));
        mvc.perform(post("/api/bookings/{id}/extend",booking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"newCheckOutDate\":\""+in.plusDays(4)+"\"}"))
            .andExpect(status().isCreated());
        Long after=jdbc.queryForObject("select count(*) from notifications where booking_id = ?",Long.class,java.util.UUID.fromString(booking));
        org.junit.jupiter.api.Assertions.assertEquals(before,after);
    }

    private String register(String email)throws Exception{return body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD,"passwordConfirmation",PASSWORD,"fullName","Phase Seven","phone","+254711111111")))).andExpect(status().isCreated()).andReturn()).get("accessToken").asText();}
    private String property(String token)throws Exception{return body(mvc.perform(post("/api/properties").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name","Phase Seven Property","propertyType","APARTMENT","address","1 Test Street","mapsUrl","https://maps.google.com/?q=test","maxGuests",4,"defaultNightlyRate",120,"currency","KES","checkInTime",LocalTime.of(14,0).toString(),"checkOutTime",LocalTime.of(10,0).toString(),"active",true)))).andExpect(status().isCreated()).andReturn()).get("id").asText();}
    private String guest(String token)throws Exception{return body(mvc.perform(post("/api/guests").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Phase Seven Guest\",\"phone\":\"+254722333444\",\"email\":\"phase7guest@example.com\"}")).andExpect(status().isCreated()).andReturn()).get("id").asText();}
    private String booking(String token,String property,String guest,LocalDate in,LocalDate out,String status)throws Exception{Map<String,Object> p=new LinkedHashMap<>();p.put("propertyId",property);p.put("guestId",guest);p.put("checkInDate",in);p.put("checkOutDate",out);p.put("totalAmount",new BigDecimal("450"));p.put("currency","KES");p.put("status",status);String bookingId=body(mvc.perform(post("/api/bookings").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isCreated()).andReturn()).get("id").asText();String tokenValue=guestLink(token,bookingId);mvc.perform(put("/api/public/guest/{token}/registration",tokenValue).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Registered Guest\",\"phone\":\"+254722333444\",\"email\":\"registered."+bookingId+"@example.com\"}")).andExpect(status().isNoContent());return bookingId;}
    private String guestLink(String token,String booking)throws Exception{return body(mvc.perform(post("/api/bookings/{id}/guest-link",booking).header("Authorization",bearer(token))).andExpect(status().isCreated()).andReturn()).get("token").asText();}
    private void paySuccess(String token,String booking,String event)throws Exception{MvcResult p=mvc.perform(post("/api/bookings/{id}/payments",booking).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MPESA\"}")).andExpect(status().isCreated()).andReturn();webhook(body(p).get("providerReference").asText(),event,true);}
    private void webhook(String ref,String event,boolean success)throws Exception{mvc.perform(post("/api/webhooks/mpesa").header("X-Mpesa-Webhook-Secret",WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("providerReference",ref,"eventId",event,"success",success)))).andExpect(status().isNoContent());}
    private String hash(String value) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    private JsonNode body(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString());} private String bearer(String t){return "Bearer "+t;}
}
