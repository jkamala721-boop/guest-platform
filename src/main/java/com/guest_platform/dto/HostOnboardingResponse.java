package com.guest_platform.dto;
import com.guest_platform.entity.HostVerificationStatus;
public record HostOnboardingResponse(VerificationCheck verification,AgreementCheck agreement,CompletionCheck property,CompletionCheck payout,boolean ready){
 public record VerificationCheck(HostVerificationStatus status,boolean complete){} public record AgreementCheck(boolean complete,String version){} public record CompletionCheck(boolean complete){}
}
