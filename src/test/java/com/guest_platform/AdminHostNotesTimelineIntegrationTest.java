package com.guest_platform;
import static org.assertj.core.api.Assertions.assertThat; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID; import java.time.*; import java.math.BigDecimal; import jakarta.servlet.http.Cookie; import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest; import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.test.context.ActiveProfiles; import org.springframework.test.web.servlet.MockMvc;
import com.guest_platform.entity.*; import com.guest_platform.repository.*; import com.guest_platform.service.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:admin-host-notes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test") class AdminHostNotesTimelineIntegrationTest {
 @Autowired MockMvc mvc; @Autowired PasswordEncoder encoder; @Autowired HostRepository hosts; @Autowired AdminUserRepository admins;
 @Autowired AdminSessionService sessions; @Autowired HostSessionService hostSessions; @Autowired AdminAuditLogRepository audits;
 @Autowired HostVerificationService verification;
 @Autowired HostAgreementService agreements; @Autowired PropertyRepository properties; @Autowired BookingRepository bookings;
 @Autowired PaymentRepository payments; @Autowired HostPayoutRepository payouts; @Autowired HostPayoutSettingsRepository payoutSettings;

 @Test void everyAdminRoleCreatesInternalNoteWithAuthenticatedAuthorAndAuditWithoutContent() throws Exception {
  Host host=host();for(AdminRole role:AdminRole.values()){AdminUser author=adminUser(role);String secret="internal-secret-"+UUID.randomUUID();
   mvc.perform(post("/api/admin/hosts/{id}/notes",host.getId()).cookie(cookie(author)).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"GENERAL\",\"content\":\"  "+secret+"  \",\"authorAdminId\":\""+UUID.randomUUID()+"\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(secret)).andExpect(jsonPath("$.authorAdminId").value(author.getId().toString()));
   assertThat(audits.findAll()).filteredOn(a->AdminAuditService.ADMIN_HOST_NOTE_CREATED.equals(a.getAction())&&a.getAdminUser().getId().equals(author.getId())).allMatch(a->!String.valueOf(a.getReason()).contains(secret));}}

 @Test void noteValidationAuthorizationFilteringAndPaginationAreSafe() throws Exception {
  Host host=host();Cookie support=cookie(adminUser(AdminRole.SUPPORT));
  mvc.perform(post("/api/admin/hosts/{id}/notes",host.getId()).cookie(support).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"GENERAL\",\"content\":\" \"}" )).andExpect(status().isBadRequest());
  mvc.perform(post("/api/admin/hosts/{id}/notes",host.getId()).cookie(support).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"GENERAL\",\"content\":\""+"x".repeat(5001)+"\"}" )).andExpect(status().isBadRequest());
  mvc.perform(post("/api/admin/hosts/{id}/notes",UUID.randomUUID()).cookie(support).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"GENERAL\",\"content\":\"note\"}" )).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_HOST_NOT_FOUND"));
  create(host,support,"GENERAL","older");Thread.sleep(2);create(host,support,"RISK","newer");
  mvc.perform(get("/api/admin/hosts/{id}/notes",host.getId()).param("size","1").cookie(support)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].content").value("newer")).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
  mvc.perform(get("/api/admin/hosts/{id}/notes",host.getId()).param("type","GENERAL").cookie(support)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].content").value("older"));
  mvc.perform(get("/api/admin/hosts/{id}/notes",host.getId())).andExpect(status().isUnauthorized());String token=hostSessions.create(host).value();mvc.perform(get("/api/admin/hosts/{id}/notes",host.getId()).cookie(new Cookie("HOSTVERO_SESSION",token))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/me/verification").cookie(new Cookie("HOSTVERO_SESSION",token))).andExpect(status().isOk()).andExpect(jsonPath("$.notes").doesNotExist());
 }

 @Test void timelineIncludesSafeVerificationAndNoteEventsNewestFirst() throws Exception {
  Host host=host();Cookie operations=cookie(adminUser(AdminRole.OPERATIONS));verification.submit(host.getId(),new com.guest_platform.dto.HostVerificationSubmissionRequest("Safe Legal",HostVerificationType.INDIVIDUAL,HostIdentityType.NATIONAL_ID,"12345678","+254712345678","KE"));
  AdminUser reviewer=adminUser(AdminRole.OPERATIONS);verification.startReview(reviewer.getId(),host.getId());create(host,operations,"VERIFICATION","Reviewed documents safely");
  String body=mvc.perform(get("/api/admin/hosts/{id}/timeline",host.getId()).cookie(operations)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].eventType").value("ADMIN_HOST_NOTE_CREATED")).andExpect(jsonPath("$.items[?(@.eventType == 'HOST_VERIFICATION_SUBMITTED')]").isNotEmpty()).andExpect(jsonPath("$.items[?(@.eventType == 'HOST_VERIFICATION_REVIEW_STARTED')]").isNotEmpty()).andReturn().getResponse().getContentAsString();
  assertThat(body).doesNotContain("12345678").doesNotContain("idFingerprint").doesNotContain("recipientCode");
  mvc.perform(get("/api/admin/hosts/{id}/timeline",UUID.randomUUID()).cookie(operations)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_HOST_NOT_FOUND"));String token=hostSessions.create(host).value();mvc.perform(get("/api/admin/hosts/{id}/timeline",host.getId()).cookie(new Cookie("HOSTVERO_SESSION",token))).andExpect(status().isUnauthorized());
 }
 @Test void timelineIncludesAgreementBookingAndPayoutWithoutOperationalSecrets() throws Exception {
  Host host=host();AdminUser superAdmin=adminUser(AdminRole.SUPER_ADMIN);Cookie cookie=cookie(superAdmin);String version="tl-"+UUID.randomUUID().toString().substring(0,8);
  var agreement=agreements.create(superAdmin.getId(),new com.guest_platform.dto.AdminAgreementCreateRequest(version,"Timeline Agreement","Safe terms",Instant.now(),true,true));agreements.accept(host.getId(),new com.guest_platform.dto.AgreementAcceptanceRequest(agreement.version()),"203.0.113.2",null);
  Property property=new Property(host);property.update("Timeline Property",PropertyType.APARTMENT,"Nairobi","https://maps.example/timeline",2,new BigDecimal("3500"),"KES",LocalTime.of(14,0),LocalTime.of(10,0),null,null,null,null,"+254700000000",true,null,null,null,null,null);properties.saveAndFlush(property);
  Booking booking=new Booking(host,property);booking.update(property,LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),new BigDecimal("3500"),"KES",BookingStatus.CONFIRMED,null);bookings.saveAndFlush(booking);
  Payment payment=new Payment(host,booking,PaymentProvider.PAYSTACK,"timeline-payment-"+UUID.randomUUID(),new BigDecimal("3500"),"KES");payment.markSucceeded("timeline-event-"+UUID.randomUUID());payments.saveAndFlush(payment);
  String recipient="RCP_TIMELINE_"+UUID.randomUUID();HostPayoutSettings setting=payoutSettings.saveAndFlush(new HostPayoutSettings(host,PayoutMethod.MPESA,null,null,null,null,recipient,"6789","sensitive-fingerprint"));HostPayout payout=payouts.saveAndFlush(new HostPayout(payment,recipient));payout.releaseIfEligible(setting,Instant.now(),Duration.ZERO);payouts.saveAndFlush(payout);
  String body=mvc.perform(get("/api/admin/hosts/{id}/timeline",host.getId()).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.eventType == 'HOST_AGREEMENT_ACCEPTED')]").isNotEmpty()).andExpect(jsonPath("$.items[?(@.eventType == 'BOOKING_CONFIRMED')]").isNotEmpty()).andExpect(jsonPath("$.items[?(@.eventType == 'HOST_PAYOUT_AVAILABLE')]").isNotEmpty()).andReturn().getResponse().getContentAsString();
  assertThat(body).doesNotContain(recipient).doesNotContain("sensitive-fingerprint").doesNotContain("ipAddressHash");
 }
 private void create(Host h,Cookie c,String type,String content)throws Exception{mvc.perform(post("/api/admin/hosts/{id}/notes",h.getId()).cookie(c).header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\""+type+"\",\"content\":\""+content+"\"}" )).andExpect(status().isOk());}
 private Host host(){return hosts.saveAndFlush(new Host("notes-"+UUID.randomUUID()+"@example.com",encoder.encode("StrongPass!123"),"Notes Host","+254712345678"));}
 private AdminUser adminUser(AdminRole role){return admins.saveAndFlush(new AdminUser("notes-admin-"+UUID.randomUUID()+"@example.com",encoder.encode("StrongPass!123"),role.name(),role));}
 private Cookie cookie(AdminUser a){return new Cookie("HOSTVERO_ADMIN_SESSION",sessions.create(a).value());}
}
