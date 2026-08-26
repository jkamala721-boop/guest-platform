package com.guest_platform.dto;
import java.time.Instant; import java.util.UUID; import com.guest_platform.entity.*;
public record AdminHostVerificationResponse(UUID hostId,String hostEmail,String hostName,HostAccountStatus accountStatus,
 HostVerificationStatus status,Instant submittedAt,Instant reviewStartedAt,Instant reviewedAt,String legalName,
 HostVerificationType verificationType,HostIdentityType idType,String idNumberLast4,String phone,String countryCode,
 String rejectionReason,String suspensionReason){
 public static AdminHostVerificationResponse from(Host h,HostVerification v){return new AdminHostVerificationResponse(h.getId(),h.getEmail(),h.getFullName(),h.getAccountStatus(),v==null?HostVerificationStatus.UNVERIFIED:v.getStatus(),v==null?null:v.getSubmittedAt(),v==null?null:v.getReviewStartedAt(),v==null?null:v.getReviewedAt(),v==null?null:v.getLegalName(),v==null?null:v.getVerificationType(),v==null?null:v.getIdType(),v==null?null:v.getIdNumberLast4(),v==null?null:v.getPhone(),v==null?null:v.getCountryCode(),v==null?null:v.getRejectionReason(),h.getAccountSuspensionReason());}
}
