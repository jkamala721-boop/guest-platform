package com.guest_platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import com.guest_platform.service.*;
import com.guest_platform.entity.*;
import com.guest_platform.repository.*;

/** Real PostgreSQL, real Flyway migrations and independent concurrent HTTP transactions. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PropertyOfferPostgresIntegrationTest {
    static final EmbeddedPostgres POSTGRES = startPostgres();
    static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            try (Connection c = pg.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
                s.execute("create role anon"); s.execute("create role authenticated");
            }
            return pg;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.flyway.locations", () -> "classpath:db/core-migration,classpath:db/postgresql-migration");
        properties.add("app.offers.cleanup-delay-ms", () -> "3600000");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AvailabilityService availability;
    @Autowired OfferReservationService reservations;
    @Autowired NotificationService notifications;
    @Autowired PropertyOfferService offers;
    @Autowired GuestLinkService links;
    @Autowired BookingRepository bookings;
    @Autowired PaymentRepository payments;
    @Autowired PaymentService paymentService;

    record Fixture(String auth, UUID hostId, UUID property, String offerToken, UUID offerId) { }
    Fixture fixture() throws Exception { return fixture(LocalTime.of(14, 0)); }
    Fixture fixture(LocalTime checkIn) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String auth = TestSessionTokens.from(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", suffix+"@example.com", "password", "StrongPass!123",
                        "passwordConfirmation", "StrongPass!123", "fullName", "Offer Host", "phone", "+254711111111"))))
                .andExpect(status().isCreated()).andReturn());
        JsonNode property = body(mvc.perform(post("/api/properties").header("Authorization", "Bearer "+auth)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "Offer apartment",
                        "propertyType", "APARTMENT", "address", "Nairobi", "mapsUrl", "https://maps.google.com/?q=test",
                        "maxGuests", 4, "defaultNightlyRate", 999, "currency", "KES", "checkInTime", checkIn.toString(),
                        "checkOutTime", "10:00", "active", true)))).andExpect(status().isCreated()).andReturn());
        UUID propertyId = UUID.fromString(property.get("id").asText());
        JsonNode offer = body(mvc.perform(post("/api/properties/{id}/offers", propertyId).header("Authorization", "Bearer "+auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nightlyPrice\":125.50}"))
                .andExpect(status().isCreated()).andReturn());
        return new Fixture(auth, jdbc.queryForObject("select host_id from properties where id=?", UUID.class, propertyId),
                propertyId, offer.get("token").asText(), UUID.fromString(offer.get("offer").get("id").asText()));
    }
    Map<String,Object> dates(LocalDate date) { return Map.of("checkInDate", date.toString(), "checkOutDate", date.plusDays(2).toString(), "checkoutAttemptId",UUID.randomUUID().toString()); }
    LocalDate future() { return LocalDate.now(OfferBookingPolicy.ZONE).plusDays(10); }
    JsonNode reserve(Fixture f, LocalDate date) throws Exception {
        return body(mvc.perform(post("/api/public/offers/{token}/bookings", f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dates(date)))).andExpect(status().isCreated()).andReturn());
    }
    String token(JsonNode r) { return r.get("guestLink").get("token").asText(); }
    UUID id(JsonNode r) { return UUID.fromString(r.get("bookingId").asText()); }
    void register(JsonNode r) throws Exception {
        mvc.perform(put("/api/public/guest/{token}/registration", token(r)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Offer Guest\",\"phone\":\"+254722333444\",\"email\":\"guest@example.com\"}"))
                .andExpect(status().isNoContent());
    }
    void confirm(JsonNode r) throws Exception {
        mvc.perform(post("/api/public/guest/{token}/confirm-reservation", token(r))).andExpect(status().isNoContent());
    }
    void expireHold(JsonNode r) {
        jdbc.update("update bookings set checkout_hold_expires_at=? where id=?", Timestamp.from(Instant.now().minusSeconds(1)), id(r));
    }
    JsonNode payment(JsonNode r) throws Exception {
        return body(mvc.perform(post("/api/public/guest/{token}/payments", token(r)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MPESA\"}")).andExpect(status().isCreated()).andReturn());
    }
    void webhook(JsonNode payment, String event) throws Exception {
        mvc.perform(post("/api/webhooks/mpesa").header("X-Mpesa-Webhook-Secret", "phase4-test-mpesa-webhook-secret")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("providerReference",
                        payment.get("providerReference").asText(), "eventId", event, "success", true))))
                .andExpect(status().isNoContent());
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    @Test void quotesDoNotHoldAndPublicPricesAreServerCalculated() throws Exception {
        Fixture f = fixture();
        Map<String,Object> tampered = new HashMap<>(dates(future()));
        tampered.put("totalAmount", 1); tampered.put("nightlyPrice", 1); tampered.put("currency", "USD");
        tampered.put("status", "CONFIRMED"); tampered.put("propertyId", UUID.randomUUID().toString());
        mvc.perform(post("/api/public/offers/{token}/quote", f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(tampered))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(251)).andExpect(jsonPath("$.currency").value("KES"));
        assertEquals(0, count("select count(*) from bookings where property_id=?", f.property));
        JsonNode r = body(mvc.perform(post("/api/public/offers/{token}/bookings", f.offerToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tampered)))
                .andExpect(status().isCreated()).andReturn());
        assertEquals(new BigDecimal("251.00"), jdbc.queryForObject("select total_amount from bookings where id=?", BigDecimal.class, id(r)));
        assertEquals("PENDING_CONFIRMATION", jdbc.queryForObject("select status from bookings where id=?", String.class, id(r)));
        assertTrue(Instant.parse(r.get("checkoutHoldExpiresAt").asText()).isBefore(Instant.now().plusSeconds(1801)));
    }

    @Test void offerIsReusableButEachBookingHasItsOwnLinkAndTerms() throws Exception {
        Fixture f=fixture(); JsonNode a=reserve(f,future()); JsonNode b=reserve(f,future().plusDays(2));
        assertNotEquals(id(a), id(b)); assertNotEquals(token(a), token(b));
        jdbc.update("update properties set default_nightly_rate=800,currency='USD' where id=?", f.property);
        mvc.perform(post("/api/public/offers/{token}/quote", f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dates(future().plusDays(5))))).andExpect(status().isOk())
                .andExpect(jsonPath("$.nightlyPrice").value(125.5)).andExpect(jsonPath("$.currency").value("KES"));
        mvc.perform(post("/api/offers/{id}/revoke",f.offerId).header("Authorization","Bearer "+f.auth)).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/offers/{token}",f.offerToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/guest/{token}",token(a))).andExpect(status().isOk());
        mvc.perform(get("/api/public/guest/{token}",token(b))).andExpect(status().isOk());
    }

    @Test void retryDoesNotExtendHoldAndExpiryReleasesDatesWithoutCleanup() throws Exception {
        Fixture f=fixture(); JsonNode r=reserve(f,future());
        Instant deadline=jdbc.queryForObject("select checkout_hold_expires_at from bookings where id=?",Timestamp.class,id(r)).toInstant();
        mvc.perform(post("/api/public/offers/{token}/bookings",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dates(future())))).andExpect(status().isConflict());
        assertEquals(deadline,jdbc.queryForObject("select checkout_hold_expires_at from bookings where id=?",Timestamp.class,id(r)).toInstant());
        expireHold(r);
        Map<String,Object> retry=new HashMap<>(dates(future()));retry.put("checkoutAttemptId",id(r).toString());
        mvc.perform(post("/api/public/offers/{token}/bookings",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(retry))).andExpect(status().isConflict());
        assertTrue(availability.isAvailable(f.property,future(),future().plusDays(2),null));
        assertTrue(availability.getPublicCalendar(f.property,future(),future().plusDays(2)).unavailableRanges().isEmpty());
        mvc.perform(get("/api/public/guest/{token}",token(r))).andExpect(status().isNotFound());
        reserve(f,future());
        reservations.expire(id(r));
        assertEquals("CANCELLED",jdbc.queryForObject("select status from bookings where id=?",String.class,id(r)));
    }

    @Test void futureRegistrationRequiresExplicitConfirmationAndExpiresAtPaymentDeadline() throws Exception {
        Fixture f=fixture(); JsonNode r=reserve(f,future());
        mvc.perform(post("/api/public/guest/{token}/confirm-reservation",token(r))).andExpect(status().isConflict());
        register(r);
        assertNull(jdbc.queryForObject("select reservation_confirmed_at from bookings where id=?",Timestamp.class,id(r)));
        mvc.perform(post("/api/public/guest/{token}/payments",token(r)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MPESA\"}")).andExpect(status().isConflict());
        confirm(r); Timestamp confirmed=jdbc.queryForObject("select reservation_confirmed_at from bookings where id=?",Timestamp.class,id(r));
        mvc.perform(post("/api/bookings/{id}/payments",id(r)).header("Authorization","Bearer "+f.auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MPESA\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/{id}/payments/cash/confirm",id(r)).header("Authorization","Bearer "+f.auth))
                .andExpect(status().isConflict());
        confirm(r); assertEquals(confirmed,jdbc.queryForObject("select reservation_confirmed_at from bookings where id=?",Timestamp.class,id(r)));
        expireHold(r);
        assertFalse(availability.isAvailable(f.property,future(),future().plusDays(2),null));
        Instant checkIn=future().atTime(14,0).atZone(OfferBookingPolicy.ZONE).toInstant();
        assertEquals(checkIn.minusSeconds(43200),jdbc.queryForObject("select payment_deadline_at from bookings where id=?",Timestamp.class,id(r)).toInstant());
        assertEquals(checkIn.minusSeconds(86400),jdbc.queryForObject("select scheduled_at from notifications where booking_id=? and type='TWENTY_FOUR_HOUR_PAYMENT_REQUEST'",Timestamp.class,id(r)).toInstant());
        notifications.deliverDueNotifications(checkIn.minusSeconds(86400));
        assertEquals("SENT",jdbc.queryForObject("select status from notifications where booking_id=? and type='TWENTY_FOUR_HOUR_PAYMENT_REQUEST'",String.class,id(r)));
        jdbc.update("update bookings set payment_deadline_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(1)),id(r));
        assertTrue(availability.isAvailable(f.property,future(),future().plusDays(2),null));
        mvc.perform(post("/api/public/guest/{token}/payments",token(r)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MPESA\"}")).andExpect(status().isNotFound());
    }

    @Test void bookingInsideTwelveHoursHasARealPaymentOpportunityAndVerifiedSuccessIsIdempotent() throws Exception {
        ZonedDateTime arrival=ZonedDateTime.now(OfferBookingPolicy.ZONE).plusHours(2).withNano(0);
        Fixture f=fixture(arrival.toLocalTime()); JsonNode r=reserve(f,arrival.toLocalDate()); register(r);
        Instant deadline=Instant.parse(r.get("paymentDeadlineAt").asText());
        assertTrue(deadline.isAfter(Instant.now())); assertTrue(deadline.isBefore(arrival.toInstant()));
        assertEquals(r.get("checkoutHoldExpiresAt"),r.get("paymentDeadlineAt"));
        JsonNode p=payment(r); String event=UUID.randomUUID().toString(); webhook(p,event); webhook(p,event);
        assertEquals("CONFIRMED",jdbc.queryForObject("select status from bookings where id=?",String.class,id(r)));
        assertEquals(1,count("select count(*) from receipts where booking_id=?",id(r)));
        assertEquals(0,count("select count(*) from host_payouts h join payments p on p.id=h.payment_id where p.booking_id=?",id(r)));
        mvc.perform(get("/api/public/guest/{token}",token(r))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("STAY_ACTIVE"));
        mvc.perform(get("/api/public/guest/{token}/receipt",token(r))).andExpect(status().isOk());
    }

    @Test void checkoutDeadlineNeverExceedsCheckIn() throws Exception {
        ZonedDateTime arrival=ZonedDateTime.now(OfferBookingPolicy.ZONE).plusMinutes(10).withNano(0);
        Fixture f=fixture(arrival.toLocalTime()); JsonNode r=reserve(f,arrival.toLocalDate());
        assertEquals(arrival.toInstant(),Instant.parse(r.get("checkoutHoldExpiresAt").asText()));
        assertEquals(arrival.toInstant(),Instant.parse(r.get("paymentDeadlineAt").asText()));
    }

    @Test void latePaymentIsRecordedWithoutReactivationOrPayoutEvenAfterDatesAreRebooked() throws Exception {
        ZonedDateTime arrival=ZonedDateTime.now(OfferBookingPolicy.ZONE).plusHours(3).withNano(0);
        Fixture f=fixture(arrival.toLocalTime()); JsonNode old=reserve(f,arrival.toLocalDate()); register(old); JsonNode p=payment(old);
        expireHold(old); JsonNode replacement=reserve(f,arrival.toLocalDate());
        String event=UUID.randomUUID().toString(); webhook(p,event); webhook(p,event);
        assertEquals("SUCCEEDED",jdbc.queryForObject("select status from payments where booking_id=?",String.class,id(old)));
        assertNotNull(jdbc.queryForObject("select reservation_resolution_required_at from payments where booking_id=?",Timestamp.class,id(old)));
        assertEquals("CANCELLED",jdbc.queryForObject("select status from bookings where id=?",String.class,id(old)));
        assertEquals(0,count("select count(*) from host_payouts h join payments p on p.id=h.payment_id where p.booking_id=?",id(old)));
        assertEquals(1,count("select count(*) from receipts where booking_id=?",id(old)));
        mvc.perform(get("/api/public/guest/{token}",token(old))).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/guest/{token}",token(replacement))).andExpect(status().isOk());
    }

    @Test void expiredAndRevokedOffersRejectNewReservationsAndOwnershipIsEnforced() throws Exception {
        Fixture f=fixture(), other=fixture();
        mvc.perform(post("/api/offers/{id}/revoke",f.offerId).header("Authorization","Bearer "+other.auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/properties/{id}/offers",f.property).header("Authorization","Bearer "+other.auth)).andExpect(status().isNotFound());
        jdbc.update("update property_offers set created_at=?,expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(700000)),Timestamp.from(Instant.now().minusSeconds(1)),f.offerId);
        mvc.perform(post("/api/public/offers/{token}/bookings",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dates(future())))).andExpect(status().isNotFound());
        offers.revoke(other.hostId,other.offerId);
        mvc.perform(post("/api/public/offers/{token}/bookings",other.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dates(future())))).andExpect(status().isNotFound());
    }

    @Test void twoConcurrentReservationsCannotDoubleBook() throws Exception {
        Fixture f=fixture(); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try (ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=() -> {ready.countDown(); assertTrue(go.await(10,TimeUnit.SECONDS));
                return mvc.perform(post("/api/public/offers/{token}/bookings",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dates(future())))).andReturn().getResponse().getStatus();};
            Future<Integer> a=executor.submit(attempt),b=executor.submit(attempt);
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            List<Integer> results=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));
            Collections.sort(results); assertEquals(List.of(201,409),results);
        }
        assertEquals(1,count("select count(*) from bookings where property_id=?",f.property));
    }

    @Test void invalidDatesAndPricesAreRejectedAndPublicPreviewExcludesStaySecrets() throws Exception {
        Fixture f=fixture();
        mvc.perform(post("/api/properties/{id}/offers",f.property).header("Authorization","Bearer "+f.auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nightlyPrice\":0}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/properties/{id}/offers",f.property).header("Authorization","Bearer "+f.auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nightlyPrice\":1.234}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/public/offers/{token}/quote",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("checkInDate",future().toString(),"checkOutDate",future().toString()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/public/offers/{token}/quote",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dates(LocalDate.now(OfferBookingPolicy.ZONE).minusDays(1)))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/offers/{token}",f.offerToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenHash").doesNotExist()).andExpect(jsonPath("$.wifiPassword").doesNotExist())
                .andExpect(jsonPath("$.checkInInstructions").doesNotExist());
        mvc.perform(get("/offer/{token}",f.offerToken)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test void concurrentHostBookingAndOfferReservationShareThePropertyLock() throws Exception {
        Fixture f=fixture(); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        Map<String,Object> hostRequest=new HashMap<>(dates(future()));
        hostRequest.put("propertyId",f.property);hostRequest.put("totalAmount",300);hostRequest.put("currency","KES");
        hostRequest.put("status","PENDING_CONFIRMATION");
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Future<Integer> guest=executor.submit(() -> { ready.countDown();go.await(10,TimeUnit.SECONDS);
                return mvc.perform(post("/api/public/offers/{token}/bookings",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dates(future())))).andReturn().getResponse().getStatus(); });
            Future<Integer> host=executor.submit(() -> { ready.countDown();go.await(10,TimeUnit.SECONDS);
                return mvc.perform(post("/api/bookings").header("Authorization","Bearer "+f.auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(hostRequest))).andReturn().getResponse().getStatus(); });
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            List<Integer> results=new ArrayList<>(List.of(guest.get(20,TimeUnit.SECONDS),host.get(20,TimeUnit.SECONDS)));
            Collections.sort(results);assertEquals(List.of(201,409),results);
        }
    }

    @Test void offerReservationRespectsPendingExtensionsAndBackToBackBookings() throws Exception {
        Fixture f=fixture();
        Map<String,Object> hostRequest=new HashMap<>();
        hostRequest.put("propertyId",f.property);hostRequest.put("checkInDate",future().minusDays(2).toString());
        hostRequest.put("checkOutDate",future().toString());hostRequest.put("totalAmount",300);
        hostRequest.put("currency","KES");hostRequest.put("status","CONFIRMED");
        JsonNode original=body(mvc.perform(post("/api/bookings").header("Authorization","Bearer "+f.auth)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(hostRequest)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/bookings/{id}/extend",original.get("id").asText()).header("Authorization","Bearer "+f.auth)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("newCheckOutDate",future().plusDays(1).toString()))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/public/offers/{token}/bookings",f.offerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dates(future())))).andExpect(status().isConflict());
        reserve(f,future().plusDays(1));
    }

    @Test void lateCallbackAfterCleanupRecordsSuccessEvenAfterEarlierFailure() throws Exception {
        ZonedDateTime arrival=ZonedDateTime.now(OfferBookingPolicy.ZONE).plusHours(3).withNano(0);
        Fixture f=fixture(arrival.toLocalTime());JsonNode r=reserve(f,arrival.toLocalDate());register(r);JsonNode p=payment(r);
        jdbc.update("update payments set status='FAILED' where booking_id=?",id(r));
        expireHold(r);reservations.expire(id(r));
        webhook(p,UUID.randomUUID().toString());
        assertNotNull(jdbc.queryForObject("select reservation_resolution_required_at from payments where booking_id=?",Timestamp.class,id(r)));
        assertEquals(1,count("select count(*) from receipts where booking_id=?",id(r)));
        assertEquals(0,count("select count(*) from host_payouts h join payments p on p.id=h.payment_id where p.booking_id=?",id(r)));
    }

    @Test void latePaystackSuccessDoesNotCreateAnOtherwiseEligiblePayout() throws Exception {
        ZonedDateTime arrival=ZonedDateTime.now(OfferBookingPolicy.ZONE).plusHours(3).withNano(0);
        Fixture f=fixture(arrival.toLocalTime());JsonNode r=reserve(f,arrival.toLocalDate());register(r);
        Booking booking=bookings.findById(id(r)).orElseThrow();
        Payment p=new Payment(booking.getHost(),booking,PaymentProvider.PAYSTACK,"offer-paystack-"+UUID.randomUUID(),
                new BigDecimal("251.00"),new BigDecimal("12.55"),new BigDecimal("263.55"),"KES");
        p.setPaystackPayoutDestination(PayoutMethod.MPESA,"RCP_offer_test");
        p=payments.saveAndFlush(p);
        expireHold(r);reservations.expire(id(r));
        var callback=new PaymentService.PaystackWebhookPayment(p.getId(),id(r),p.getProviderReference(),
                UUID.randomUUID().toString(),26355L,"KES",100L,"mobile_money");
        paymentService.processVerifiedPaystackWebhook(callback);
        paymentService.processVerifiedPaystackWebhook(callback);
        assertNotNull(jdbc.queryForObject("select reservation_resolution_required_at from payments where id=?",Timestamp.class,p.getId()));
        assertEquals(0,count("select count(*) from host_payouts where payment_id=?",p.getId()));
        assertEquals(1,count("select count(*) from receipts where payment_id=?",p.getId()));
    }

    @Test void revocationWhileReservationWaitsForPropertyLockIsRespected() throws Exception {
        Fixture f=fixture();
        try (Connection blocker=POSTGRES.getPostgresDatabase().getConnection(); ExecutorService executor=Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement s=blocker.prepareStatement("select id from properties where id=? for update")) {
                s.setObject(1,f.property);s.executeQuery().close();
            }
            Future<Integer> pending=executor.submit(() -> mvc.perform(post("/api/public/offers/{token}/bookings",f.offerToken)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dates(future())))).andReturn().getResponse().getStatus());
            try {
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while (count("select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%properties%'")==0) {
                    if(System.nanoTime()>until) fail("Reservation never waited on the property lock");
                    Thread.sleep(20);
                }
                offers.revoke(f.hostId,f.offerId);
            } finally { blocker.rollback(); }
            assertEquals(404,pending.get(20,TimeUnit.SECONDS));
        }
        assertEquals(0,count("select count(*) from bookings where property_id=?",f.property));
    }
}
