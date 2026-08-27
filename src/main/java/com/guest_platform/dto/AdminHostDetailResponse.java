package com.guest_platform.dto;

import java.time.Instant;
import java.util.*;
import com.guest_platform.entity.*;

public record AdminHostDetailResponse(UUID id,String email,String fullName,String phone,HostAccountStatus accountStatus,
        String suspensionReason,Instant createdAt,Verification verification,Agreement agreement,
        Properties properties,Payout payout,BookingActivity bookingActivity) {
    public record Verification(HostVerificationStatus status,Instant submittedAt,Instant reviewedAt,
            String rejectionReason,String legalName,HostVerificationType verificationType,HostIdentityType idType,
            String idNumberLast4,String countryCode,String phone) {}
    public record Agreement(String version,boolean accepted,Instant acceptedAt) {}
    public record Properties(long totalCount,long activeCount,List<PropertyItem> items) {}
    public record PropertyItem(UUID propertyId,String name,PropertyType propertyType,boolean active,String address,
            String mapsUrl,Instant createdAt) {}
    public record Payout(boolean configured,String provider,PayoutMethod payoutMethod,PayoutSettingsStatus status,
            String destinationLast4) {}
    public record BookingActivity(long totalBookings,Map<BookingStatus,Long> countsByStatus,long totalConfirmedBookings,
            long totalCancelledBookings,Instant mostRecentActivityAt) {}
}
