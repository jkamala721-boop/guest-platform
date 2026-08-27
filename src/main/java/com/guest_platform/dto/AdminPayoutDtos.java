package com.guest_platform.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.List; import java.util.UUID;
import com.guest_platform.entity.*;
public final class AdminPayoutDtos { private AdminPayoutDtos() {}
 public record Item(UUID payoutId,UUID hostId,String hostEmail,String hostName,UUID bookingId,BigDecimal amount,
  String currency,HostPayoutStatus status,PaymentProvider provider,PayoutMethod payoutMethod,String maskedDestination,
  String destinationLast4,String externalReference,Instant createdAt,Instant processedAt,String failureReason) {}
 public record PageResponse(List<Item> items,int page,int size,long totalElements,int totalPages) {}
 public record Detail(Item payout,String providerStatus,int attemptCount,boolean retryable,Instant lastAttemptAt,Instant updatedAt) {}
}
