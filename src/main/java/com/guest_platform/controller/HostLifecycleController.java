package com.guest_platform.controller;
import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import org.springframework.http.HttpHeaders; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import com.guest_platform.dto.*; import com.guest_platform.security.CurrentHost; import com.guest_platform.service.*;
@RestController @RequestMapping("/api/me")
public class HostLifecycleController {
 private final HostVerificationService verification; private final HostAgreementService agreements; private final HostOnboardingService onboarding; private final HostOperationalAccessService operationalAccess;
 public HostLifecycleController(HostVerificationService verification,HostAgreementService agreements,HostOnboardingService onboarding,HostOperationalAccessService operationalAccess){this.verification=verification;this.agreements=agreements;this.onboarding=onboarding;this.operationalAccess=operationalAccess;}
 @GetMapping("/verification") public HostVerificationResponse verification(Authentication auth){return verification.getForHost(CurrentHost.id(auth));}
 @PostMapping("/verification") public HostVerificationResponse submit(Authentication auth,@Valid @RequestBody HostVerificationSubmissionRequest request){return verification.submit(CurrentHost.id(auth),request);}
 @GetMapping("/agreement") public AgreementResponse agreement(Authentication auth){return agreements.current(CurrentHost.id(auth));}
 @PostMapping("/agreement/accept") public AgreementResponse accept(Authentication auth,@Valid @RequestBody AgreementAcceptanceRequest request,HttpServletRequest servletRequest){return agreements.accept(CurrentHost.id(auth),request,servletRequest.getRemoteAddr(),servletRequest.getHeader(HttpHeaders.USER_AGENT));}
 @GetMapping("/onboarding") public HostOnboardingResponse onboarding(Authentication auth){return onboarding.get(CurrentHost.id(auth));}
 @GetMapping("/operational-access") public HostOperationalAccessResponse operationalAccess(Authentication auth){return operationalAccess.get(CurrentHost.id(auth));}
}
