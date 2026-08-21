package com.guest_platform.dto;
import java.math.BigDecimal; import java.time.Instant; import java.time.LocalDate; import java.util.UUID;
import com.guest_platform.entity.BookingExtension; import com.guest_platform.entity.BookingExtensionStatus;
public record BookingExtensionResponse(UUID id, UUID bookingId, LocalDate originalCheckOutDate, LocalDate requestedCheckOutDate,
        int addedNights, BigDecimal originalBookingAmount, BigDecimal additionalAmount, BigDecimal resultingTotalAmount,
        String currency, BookingExtensionStatus status, Instant expiresAt) {
    public static BookingExtensionResponse from(BookingExtension value) { return new BookingExtensionResponse(value.getId(), value.getBooking().getId(), value.getOriginalCheckOutDate(), value.getRequestedCheckOutDate(), value.getAddedNights(), value.getOriginalBookingAmount(), value.getAdditionalAmount(), value.getResultingTotalAmount(), value.getCurrency(), value.getStatus(), value.getExpiresAt()); }
}
