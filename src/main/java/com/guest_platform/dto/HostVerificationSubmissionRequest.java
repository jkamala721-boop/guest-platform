package com.guest_platform.dto;
import com.guest_platform.entity.*; import jakarta.validation.constraints.*;
public record HostVerificationSubmissionRequest(
 @NotBlank @Size(max=160) String legalName,
 @NotNull HostVerificationType verificationType,
 @NotNull HostIdentityType idType,
 @NotBlank @Size(max=40) String idNumber,
 @NotBlank @Size(max=32) String phone,
 @NotBlank @Size(min=2,max=2) String countryCode) {}
