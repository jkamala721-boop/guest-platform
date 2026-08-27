package com.guest_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.guest_platform.dto.*;
import com.guest_platform.entity.*;
import com.guest_platform.repository.*;
import com.guest_platform.service.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:admin-host-operations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class AdminHostOperationsIntegrationTest {
    private static final String ADMIN_COOKIE="HOSTVERO_ADMIN_SESSION";
    @Autowired MockMvc mvc; @Autowired PasswordEncoder encoder;
    @Autowired HostRepository hosts; @Autowired AdminUserRepository admins; @Autowired PropertyRepository properties;
    @Autowired HostPayoutSettingsRepository payouts; @Autowired BookingRepository bookings;
    @Autowired HostVerificationService verification; @Autowired HostAgreementService agreements;
    @Autowired AdminSessionService adminSessions; @Autowired HostSessionService hostSessions;

    @Test void allAdminRolesCanUseReadEndpoints() throws Exception {
        Host host=host("role-target",HostAccountStatus.ACTIVE);
        mvc.perform(get("/api/admin/hosts").cookie(adminCookie(AdminRole.SUPER_ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/hosts").cookie(adminCookie(AdminRole.OPERATIONS))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/hosts/{id}",host.getId()).cookie(adminCookie(AdminRole.SUPPORT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(host.getId().toString()));
        mvc.perform(get("/api/admin/hosts/{id}",host.getId()).cookie(adminCookie(AdminRole.FINANCE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(host.getEmail()));
    }

    @Test void unauthenticatedAndNormalHostSessionsCannotReadAdminHosts() throws Exception {
        mvc.perform(get("/api/admin/hosts")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_AUTH_REQUIRED"));
        Host host=host("normal-session",HostAccountStatus.ACTIVE);
        String token=hostSessions.create(host).value();
        mvc.perform(get("/api/admin/hosts").cookie(new Cookie("HOSTVERO_SESSION",token)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_AUTH_REQUIRED"));
    }

    @Test void searchStatusFiltersAndPaginationAreDatabaseBacked() throws Exception {
        String key=UUID.randomUUID().toString().substring(0,8);
        Host emailMatch=host("email-"+key,HostAccountStatus.ACTIVE);
        Host nameMatch=hosts.saveAndFlush(new Host("other-"+UUID.randomUUID()+"@example.com",encoder.encode("StrongPass!123"),
                "Search Name "+key,"+254733445566"));
        Host suspended=host("suspended-"+key,HostAccountStatus.SUSPENDED);
        Host verifiedFilter=host("verified-filter-"+key,HostAccountStatus.ACTIVE);
        verification.submit(verifiedFilter.getId(),verificationRequest("12345678"));
        Cookie admin=adminCookie(AdminRole.OPERATIONS);
        mvc.perform(get("/api/admin/hosts").param("q",emailMatch.getEmail().toUpperCase()).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].hostId").value(emailMatch.getId().toString()));
        mvc.perform(get("/api/admin/hosts").param("q",("search name "+key).toUpperCase()).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hostId").value(nameMatch.getId().toString()));
        mvc.perform(get("/api/admin/hosts").param("q","+254733445566").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hostId").value(nameMatch.getId().toString()));
        mvc.perform(get("/api/admin/hosts").param("q",key).param("accountStatus","SUSPENDED").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hostId").value(suspended.getId().toString()));
        mvc.perform(get("/api/admin/hosts").param("q",key).param("verificationStatus","SUBMITTED").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hostId").value(verifiedFilter.getId().toString()));
        mvc.perform(get("/api/admin/hosts").param("q",key).param("page","0").param("size","2").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test void detailAggregatesSafeOperationalStateWithoutSecrets() throws Exception {
        String key=UUID.randomUUID().toString().substring(0,8);
        Host host=host("detail-"+key,HostAccountStatus.ACTIVE);
        String rawId="AB123456";
        verification.submit(host.getId(),new HostVerificationSubmissionRequest("Detail Legal",HostVerificationType.INDIVIDUAL,
                HostIdentityType.PASSPORT,rawId,"+243812345678","CD"));
        AdminUser creator=admin(AdminRole.SUPER_ADMIN);
        AgreementResponse current=agreements.create(creator.getId(),new AdminAgreementCreateRequest("ops-"+key,
                "Operational Agreement","Safe legal terms",Instant.now(),true,true));
        agreements.accept(host.getId(),new AgreementAcceptanceRequest(current.version()),"203.0.113.10",null);
        Property active=property(host,"Active "+key,true); Property inactive=property(host,"Inactive "+key,false);
        properties.saveAllAndFlush(java.util.List.of(active,inactive));
        payouts.saveAndFlush(new HostPayoutSettings(host,PayoutMethod.BANK_ACCOUNT,"001","4321","Detail Legal",
                "SUB_SECRET_"+key,null,null,null));
        booking(host,active,BookingStatus.CONFIRMED); booking(host,active,BookingStatus.CANCELLED);
        booking(host,active,BookingStatus.DRAFT);
        MvcResult result=mvc.perform(get("/api/admin/hosts/{id}",host.getId()).cookie(adminCookie(AdminRole.SUPPORT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verification.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.verification.idNumberLast4").value("3456"))
                .andExpect(jsonPath("$.verification.countryCode").value("CD"))
                .andExpect(jsonPath("$.agreement.version").value(current.version()))
                .andExpect(jsonPath("$.agreement.accepted").value(true))
                .andExpect(jsonPath("$.agreement.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.properties.totalCount").value(2))
                .andExpect(jsonPath("$.properties.activeCount").value(1))
                .andExpect(jsonPath("$.payout.configured").value(true))
                .andExpect(jsonPath("$.payout.destinationLast4").value("4321"))
                .andExpect(jsonPath("$.bookingActivity.totalBookings").value(3))
                .andExpect(jsonPath("$.bookingActivity.totalConfirmedBookings").value(1))
                .andExpect(jsonPath("$.bookingActivity.totalCancelledBookings").value(1))
                .andExpect(jsonPath("$.verification.idNumber").doesNotExist())
                .andExpect(jsonPath("$.verification.idFingerprint").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.payout.paystackSubaccountCode").doesNotExist())
                .andExpect(jsonPath("$.sessionToken").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawId).doesNotContain("SUB_SECRET_"+key);
    }

    @Test void unknownHostReturnsNamedNotFoundError() throws Exception {
        mvc.perform(get("/api/admin/hosts/{id}",UUID.randomUUID()).cookie(adminCookie(AdminRole.FINANCE)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_HOST_NOT_FOUND"));
    }

    private Cookie adminCookie(AdminRole role){AdminSessionService.SessionToken token=adminSessions.create(admin(role));return new Cookie(ADMIN_COOKIE,token.value());}
    private AdminUser admin(AdminRole role){String key=UUID.randomUUID().toString();return admins.saveAndFlush(new AdminUser("ops-admin-"+key+"@example.com",encoder.encode("StrongPass!123"),role.name(),role));}
    private Host host(String marker,HostAccountStatus status){Host host=hosts.saveAndFlush(new Host(marker+"-"+UUID.randomUUID()+"@example.com",encoder.encode("StrongPass!123"),"Host "+marker,"+254712345678"));if(status==HostAccountStatus.SUSPENDED){host.suspend("Operational hold");return hosts.saveAndFlush(host);}return host;}
    private HostVerificationSubmissionRequest verificationRequest(String id){return new HostVerificationSubmissionRequest("Legal Host",HostVerificationType.INDIVIDUAL,HostIdentityType.NATIONAL_ID,id,"+254712345678","KE");}
    private Property property(Host host,String name,boolean active){Property property=new Property(host);property.update(name,PropertyType.APARTMENT,"Nairobi, Kenya","https://maps.example/"+UUID.randomUUID(),2,new BigDecimal("1000"),"KES",LocalTime.of(14,0),LocalTime.of(10,0),null,null,null,null,"+254700000000",active,null,null,null,null,null);return property;}
    private void booking(Host host,Property property,BookingStatus status){Booking booking=new Booking(host,property);booking.update(property,LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),new BigDecimal("1000"),"KES",status,null);bookings.saveAndFlush(booking);}
}
