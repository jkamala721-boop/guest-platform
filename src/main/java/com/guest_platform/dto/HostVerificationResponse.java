package com.guest_platform.dto;
import java.time.Instant; import com.guest_platform.entity.*;
public record HostVerificationResponse(HostVerificationStatus status,HostAccountStatus accountStatus,Instant submittedAt,Instant reviewedAt,
 String rejectionReason,String suspensionReason,String legalName,HostVerificationType verificationType,
 HostIdentityType idType,String idNumberLast4,String phone,String countryCode) {
 public static HostVerificationResponse unverified(Host host){return new HostVerificationResponse(HostVerificationStatus.UNVERIFIED,host.getAccountStatus(),null,null,null,host.getAccountStatus()==HostAccountStatus.SUSPENDED?host.getAccountSuspensionReason():null,null,null,null,null,null,null);}
 public static HostVerificationResponse from(HostVerification v,String accountSuspensionReason){return new HostVerificationResponse(v.getStatus(),v.getHost().getAccountStatus(),v.getSubmittedAt(),v.getReviewedAt(),v.getStatus()==HostVerificationStatus.REJECTED?v.getRejectionReason():null,accountSuspensionReason,v.getLegalName(),v.getVerificationType(),v.getIdType(),v.getIdNumberLast4(),v.getPhone(),v.getCountryCode());}
}
