package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal; import java.time.*; import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest; import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles; import org.springframework.test.web.servlet.MockMvc;
import com.guest_platform.entity.*; import com.guest_platform.repository.*; import com.guest_platform.service.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:admin-payout-operations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class AdminPayoutOperationsIntegrationTest {
 @Autowired MockMvc mvc; @Autowired PasswordEncoder encoder; @Autowired HostRepository hosts;
 @Autowired PropertyRepository properties; @Autowired BookingRepository bookings; @Autowired PaymentRepository payments;
 @Autowired HostPayoutRepository payouts; @Autowired HostPayoutSettingsRepository settings;
 @Autowired AdminUserRepository admins; @Autowired AdminSessionService sessions; @Autowired HostSessionService hostSessions;
 @Autowired AdminAuditLogRepository audits;

 @Test void allRolesReadButOnlyFinanceAndSuperAdminConfirm() throws Exception {
  HostPayout payout=payout("roles");
  for(AdminRole role:AdminRole.values())mvc.perform(get("/api/admin/payouts").cookie(admin(role))).andExpect(status().isOk());
  mvc.perform(get("/api/admin/payouts/{id}",payout.getId()).cookie(admin(AdminRole.SUPPORT))).andExpect(status().isOk())
   .andExpect(jsonPath("$.payout.destinationLast4").value("6789"));
  for(AdminRole role:new AdminRole[]{AdminRole.SUPPORT,AdminRole.OPERATIONS})mvc.perform(post("/api/admin/payouts/{id}/manual-confirm",payout.getId()).cookie(admin(role)).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"externalReference\":\"MANUAL-DENIED\"}" )).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
  mvc.perform(post("/api/admin/payouts/{id}/manual-confirm",payout.getId()).cookie(admin(AdminRole.FINANCE)).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"externalReference\":\"MANUAL-001\",\"note\":\"Bank receipt checked\",\"amount\":1,\"currency\":\"USD\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.payout.status").value("PAID")).andExpect(jsonPath("$.payout.amount").value(3500.00)).andExpect(jsonPath("$.payout.currency").value("KES")).andExpect(jsonPath("$.payout.externalReference").value("MANUAL-001"));
  HostPayout stored=payouts.findById(payout.getId()).orElseThrow();assertThat(stored.getAmount()).isEqualByComparingTo("3500.00");assertThat(stored.getCurrency()).isEqualTo("KES");
  assertThat(audits.findAll()).anyMatch(a->AdminAuditService.HOST_PAYOUT_MANUAL_CONFIRMED.equals(a.getAction())&&payout.getId().toString().equals(a.getEntityId())&&a.getReason().contains("MANUAL-001"));
 }

 @Test void confirmationIsIdempotentAndInvalidStatesConflict() throws Exception {
  HostPayout payout=payout("idempotent");Cookie finance=admin(AdminRole.FINANCE);String body="{\"externalReference\":\"MANUAL-IDEMPOTENT\"}";
  mvc.perform(post("/api/admin/payouts/{id}/manual-confirm",payout.getId()).cookie(finance).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  mvc.perform(post("/api/admin/payouts/{id}/manual-confirm",payout.getId()).cookie(finance).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  assertThat(audits.findAll().stream().filter(a->AdminAuditService.HOST_PAYOUT_MANUAL_CONFIRMED.equals(a.getAction())&&payout.getId().toString().equals(a.getEntityId())).count()).isEqualTo(1);
  mvc.perform(post("/api/admin/payouts/{id}/manual-confirm",payout.getId()).cookie(finance).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"externalReference\":\"DIFFERENT\"}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOST_PAYOUT_INVALID_STATE"));
  HostPayout processing=payout("processing");processing.beginProcessing();payouts.saveAndFlush(processing);
  mvc.perform(post("/api/admin/payouts/{id}/manual-confirm",processing.getId()).cookie(finance).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"externalReference\":\"STALE-ATTEMPT\"}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOST_PAYOUT_INVALID_STATE"));
 }

 @Test void listFiltersMasksSecretsAndUnknownOrUnauthorizedRequestsAreSafe() throws Exception {
  HostPayout payout=payout("safe-list");Cookie finance=admin(AdminRole.FINANCE);
  UUID hostId=payout.getHost().getId();Host host=hosts.findById(hostId).orElseThrow();String hostEmail=host.getEmail();
  mvc.perform(get("/api/admin/payouts").param("hostId",hostId.toString()).param("status","AVAILABLE").param("provider","PAYSTACK").param("payoutMethod","MPESA").param("q",hostEmail).cookie(finance))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].maskedDestination").value("M-Pesa •••• 6789"))
   .andExpect(jsonPath("$.items[0].recipientCode").doesNotExist()).andExpect(jsonPath("$.items[0].payoutDestinationReference").doesNotExist());
  mvc.perform(get("/api/admin/payouts/{id}",UUID.randomUUID()).cookie(finance)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_PAYOUT_NOT_FOUND"));
  mvc.perform(get("/api/admin/payouts")).andExpect(status().isUnauthorized());String token=hostSessions.create(host).value();
  mvc.perform(get("/api/admin/payouts").cookie(new Cookie("HOSTVERO_SESSION",token))).andExpect(status().isUnauthorized());
 }

 @Test void financeCanMarkAvailablePayoutFailedAndActionIsAudited() throws Exception {
  HostPayout payout=payout("failure");
  mvc.perform(post("/api/admin/payouts/{id}/mark-failed",payout.getId()).cookie(admin(AdminRole.SUPER_ADMIN)).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Manual bank transfer rejected\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.payout.status").value("FAILED")).andExpect(jsonPath("$.payout.failureReason").value("Manual bank transfer rejected")).andExpect(jsonPath("$.retryable").value(false));
  assertThat(audits.findAll()).anyMatch(a->AdminAuditService.HOST_PAYOUT_MARKED_FAILED.equals(a.getAction())&&payout.getId().toString().equals(a.getEntityId()));
 }

 private HostPayout payout(String marker){String key=marker+UUID.randomUUID();Host host=hosts.saveAndFlush(new Host(key+"@example.com",encoder.encode("StrongPass!123"),"Payout "+marker,"+254712345678"));Property property=new Property(host);property.update("Property "+marker,PropertyType.APARTMENT,"Nairobi","https://maps.example/test",2,new BigDecimal("3500"),"KES",LocalTime.of(14,0),LocalTime.of(10,0),null,null,null,null,"+254700000000",true,null,null,null,null,null);properties.saveAndFlush(property);Booking booking=new Booking(host,property);booking.update(property,LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),new BigDecimal("3500"),"KES",BookingStatus.PENDING_PAYMENT,null);booking.confirmAfterVerifiedPayment();bookings.saveAndFlush(booking);Payment payment=new Payment(host,booking,PaymentProvider.PAYSTACK,"PAY-"+UUID.randomUUID(),new BigDecimal("3500"),"KES");payment.markSucceeded("EVENT-"+UUID.randomUUID());payments.saveAndFlush(payment);HostPayoutSettings destination=settings.saveAndFlush(new HostPayoutSettings(host,PayoutMethod.MPESA,null,null,null,null,"RCP_SECRET_"+UUID.randomUUID(),"6789","FINGERPRINT_SECRET"));HostPayout payout=payouts.saveAndFlush(new HostPayout(payment,destination.getPaystackRecipientCode()));payout.releaseIfEligible(destination,Instant.now(),Duration.ZERO);return payouts.saveAndFlush(payout);}
 private Cookie admin(AdminRole role){AdminUser admin=admins.saveAndFlush(new AdminUser("payout-admin-"+UUID.randomUUID()+"@example.com",encoder.encode("StrongPass!123"),role.name(),role));return new Cookie("HOSTVERO_ADMIN_SESSION",sessions.create(admin).value());}
}
