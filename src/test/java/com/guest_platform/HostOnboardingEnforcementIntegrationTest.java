package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guest_platform.dto.*;
import com.guest_platform.entity.*;
import com.guest_platform.repository.*;
import com.guest_platform.service.*;

@SpringBootTest(properties = {
 "spring.datasource.url=jdbc:h2:mem:onboarding-enforcement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
 "app.onboarding.enforcement-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HostOnboardingEnforcementIntegrationTest {
 @Autowired MockMvc mvc;
 private final ObjectMapper json = new ObjectMapper();
 @Autowired PasswordEncoder encoder;
 @Autowired HostRepository hosts;
 @Autowired PropertyRepository properties;
 @Autowired BookingRepository bookings;
 @Autowired HostPayoutSettingsRepository payoutSettings;
 @Autowired AdminUserRepository admins;
 @Autowired HostSessionService sessions;
 @Autowired HostVerificationService verification;
 @Autowired HostAgreementService agreements;
 @Autowired PropertyService propertyService;

 @Test void incompleteHostCannotUseLaunchOperationsAndErrorIsSafe() throws Exception {
  Host host=host(); Property property=property(host); Cookie cookie=cookie(host);
  String response=mvc.perform(post("/api/bookings").cookie(cookie).header("Origin","http://localhost:8080")
    .contentType(MediaType.APPLICATION_JSON).content(bookingJson(property.getId())))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOST_ONBOARDING_INCOMPLETE"))
   .andExpect(jsonPath("$.validationErrors.verificationComplete").value("false"))
   .andExpect(jsonPath("$.validationErrors.propertyComplete").value("true"))
   .andReturn().getResponse().getContentAsString();
  assertThat(response).doesNotContain("fingerprint","recipient","idNumber","password","secret");

  Booking booking=booking(host,property);
  mvc.perform(post("/api/bookings/{id}/guest-link",booking.getId()).cookie(cookie)
    .header("Origin","http://localhost:8080"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOST_ONBOARDING_INCOMPLETE"));
  mvc.perform(post("/api/bookings/{id}/payments",booking.getId()).cookie(cookie)
    .header("Origin","http://localhost:8080").contentType(MediaType.APPLICATION_JSON)
    .content("{\"provider\":\"PAYSTACK\"}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOST_ONBOARDING_INCOMPLETE"));
 }

 @Test void completionActionsRemainAllowedAndReadyHostCanCreateBookingAndGuestLink() throws Exception {
  Host host=host(); AdminUser operations=admin(AdminRole.OPERATIONS); AdminUser superAdmin=admin(AdminRole.SUPER_ADMIN);
  assertThat(verification.submit(host.getId(),verificationRequest()).status()).isEqualTo(HostVerificationStatus.SUBMITTED);
  verification.startReview(operations.getId(),host.getId()); verification.approve(operations.getId(),host.getId());
  AgreementResponse agreement=agreements.create(superAdmin.getId(),new AdminAgreementCreateRequest(
    "onb-"+UUID.randomUUID().toString().substring(0,8),"Host Agreement","Safe agreement content",Instant.now(),true,true));
  assertThat(agreements.accept(host.getId(),new AgreementAcceptanceRequest(agreement.version()),"203.0.113.3",null).accepted()).isTrue();
  Property property=property(host);
  payoutSettings.saveAndFlush(new HostPayoutSettings(host,PayoutMethod.MPESA,null,null,null,null,
    "test_recipient","5678","test-fingerprint"));

  Cookie cookie=cookie(host);
  String created=mvc.perform(post("/api/bookings").cookie(cookie).header("Origin","http://localhost:8080")
    .contentType(MediaType.APPLICATION_JSON).content(bookingJson(property.getId())))
   .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  UUID bookingId=UUID.fromString(json.readTree(created).get("id").asText());
  mvc.perform(post("/api/bookings/{id}/guest-link",bookingId).cookie(cookie)
    .header("Origin","http://localhost:8080"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.token").isNotEmpty());
 }

 private Host host(){return hosts.saveAndFlush(new Host("onboarding-"+UUID.randomUUID()+"@example.com",
   encoder.encode("StrongPass!123"),"Onboarding Host","+254712345678"));}
 private AdminUser admin(AdminRole role){return admins.saveAndFlush(new AdminUser(
   "onboarding-admin-"+UUID.randomUUID()+"@example.com",encoder.encode("StrongPass!123"),role.name(),role));}
 private Cookie cookie(Host host){return new Cookie("HOSTVERO_SESSION",sessions.create(host).value());}
 private Property property(Host host){return properties.findById(propertyService.create(host.getId(),propertyRequest()).id()).orElseThrow();}
 private PropertyUpsertRequest propertyRequest(){return new PropertyUpsertRequest("First Property",PropertyType.APARTMENT,
   "Nairobi","https://maps.example/onboarding",2,new BigDecimal("3500"),"KES",LocalTime.of(14,0),LocalTime.of(10,0),
   null,null,null,null,null,null,null,null,null,"+254700000000",true);}
 private HostVerificationSubmissionRequest verificationRequest(){return new HostVerificationSubmissionRequest("Onboarding Host",
   HostVerificationType.INDIVIDUAL,HostIdentityType.NATIONAL_ID,"12345678","+254712345678","KE");}
 private Booking booking(Host host,Property property){Booking booking=new Booking(host,property);booking.update(property,
   LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),new BigDecimal("3500"),"KES",BookingStatus.PENDING_PAYMENT,null);
   return bookings.saveAndFlush(booking);}
 private String bookingJson(UUID propertyId){return "{\"propertyId\":\""+propertyId+"\",\"checkInDate\":\""+
   LocalDate.now().plusDays(1)+"\",\"checkOutDate\":\""+LocalDate.now().plusDays(2)+
   "\",\"totalAmount\":3500,\"currency\":\"KES\",\"status\":\"PENDING_PAYMENT\"}";}
}
