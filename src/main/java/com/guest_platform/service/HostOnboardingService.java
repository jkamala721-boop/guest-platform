package com.guest_platform.service;
import java.util.UUID; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import com.guest_platform.dto.HostOnboardingResponse; import com.guest_platform.entity.*; import com.guest_platform.repository.*;
@Service
public class HostOnboardingService {
 private final HostVerificationRepository verifications; private final HostAgreementService agreements; private final PropertyRepository properties; private final HostPayoutSettingsRepository payouts;
 public HostOnboardingService(HostVerificationRepository v,HostAgreementService a,PropertyRepository p,HostPayoutSettingsRepository payouts){this.verifications=v;this.agreements=a;this.properties=p;this.payouts=payouts;}
 @Transactional(readOnly=true) public HostOnboardingResponse get(UUID hostId){HostVerificationStatus status=verifications.findByHostId(hostId).map(HostVerification::getStatus).orElse(HostVerificationStatus.UNVERIFIED);boolean verified=status==HostVerificationStatus.VERIFIED;String version=agreements.currentVersionOrNull();boolean accepted=version!=null&&agreements.acceptedCurrent(hostId);boolean property=properties.existsByHostIdAndActiveTrue(hostId);boolean payout=payouts.findByHostId(hostId).map(s->s.getStatus()==PayoutSettingsStatus.CONFIGURED).orElse(false);return new HostOnboardingResponse(new HostOnboardingResponse.VerificationCheck(status,verified),new HostOnboardingResponse.AgreementCheck(accepted,version),new HostOnboardingResponse.CompletionCheck(property),new HostOnboardingResponse.CompletionCheck(payout),verified&&accepted&&property&&payout);}
}
