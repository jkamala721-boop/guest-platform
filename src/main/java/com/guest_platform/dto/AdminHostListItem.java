package com.guest_platform.dto;

import java.time.Instant;
import java.util.UUID;
import com.guest_platform.entity.*;

public record AdminHostListItem(UUID hostId,String email,String fullName,String phone,
        HostAccountStatus accountStatus,String suspensionReason,HostVerificationStatus verificationStatus,
        HostVerificationType verificationType,String identityCountryCode,boolean agreementAccepted,
        String agreementVersion,long propertyCount,long activePropertyCount,boolean payoutConfigured,
        Instant createdAt,Instant lastRelevantActivityAt) {}
