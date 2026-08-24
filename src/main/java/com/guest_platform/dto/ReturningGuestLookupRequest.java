package com.guest_platform.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ReturningGuestLookupRequest(@NotBlank @Pattern(regexp = "NATIONAL_ID|PASSPORT") String identityType,
        @NotBlank @Size(max = 100) String identityNumber) { }
