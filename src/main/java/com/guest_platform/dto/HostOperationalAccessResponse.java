package com.guest_platform.dto;

import java.time.Instant;
import com.guest_platform.entity.HostVerificationStatus;

public record HostOperationalAccessResponse(boolean accessAllowed, HostVerificationStatus verificationStatus,
        Instant verificationGraceEndsAt, long verificationDaysRemaining, boolean verificationSubmissionRequired,
        boolean accountSuspended, String code, String message) {
}
