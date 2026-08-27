package com.guest_platform.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.guest_platform.dto.HostOperationalAccessResponse;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostAccountStatus;
import com.guest_platform.entity.HostVerificationStatus;
import com.guest_platform.exception.HostOperationalAccessException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.HostRepository;
import com.guest_platform.repository.HostVerificationRepository;

@Service
public class HostOperationalAccessService {
    private final HostRepository hosts;
    private final HostVerificationRepository verifications;
    private final long graceDays;

    public HostOperationalAccessService(HostRepository hosts, HostVerificationRepository verifications,
            @Value("${app.verification.grace-period-days:14}") long graceDays) {
        this.hosts = hosts;
        this.verifications = verifications;
        this.graceDays = graceDays;
    }

    @Transactional(readOnly = true)
    public HostOperationalAccessResponse get(UUID hostId) {
        Host host = hosts.findById(hostId).orElseThrow(() -> new ResourceNotFoundException("Host was not found"));
        HostVerificationStatus verification = verifications.findByHostId(hostId)
                .map(value -> value.getStatus()).orElse(HostVerificationStatus.UNVERIFIED);
        Instant graceEndsAt = host.getCreatedAt().plus(graceDays, ChronoUnit.DAYS);
        long daysRemaining = Math.max(0, (long) Math.ceil(Duration.between(Instant.now(), graceEndsAt).toSeconds() / 86400d));

        if (host.getAccountStatus() != HostAccountStatus.ACTIVE) {
            return new HostOperationalAccessResponse(false, verification, graceEndsAt, daysRemaining, false, true,
                    "HOST_ACCOUNT_SUSPENDED", "Your account is suspended pending review. Contact Hostvero support for assistance.");
        }
        boolean submissionRequired = verification == HostVerificationStatus.UNVERIFIED
                && !Instant.now().isBefore(graceEndsAt);
        if (submissionRequired) {
            return new HostOperationalAccessResponse(false, verification, graceEndsAt, 0, true, false,
                    "HOST_VERIFICATION_REQUIRED", "Submit your identity verification to restore operational access.");
        }
        return new HostOperationalAccessResponse(true, verification, graceEndsAt, daysRemaining,
                false, false, null, null);
    }

    public void requireAccess(UUID hostId) {
        HostOperationalAccessResponse access = get(hostId);
        if (!access.accessAllowed()) {
            throw new HostOperationalAccessException(access,
                    access.accountSuspended() ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT);
        }
    }
}
