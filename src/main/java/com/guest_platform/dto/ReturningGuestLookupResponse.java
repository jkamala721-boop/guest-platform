package com.guest_platform.dto;
import java.time.Instant;
public record ReturningGuestLookupResponse(boolean verificationRequired, String maskedDestination, Instant resendAvailableAt) { }
