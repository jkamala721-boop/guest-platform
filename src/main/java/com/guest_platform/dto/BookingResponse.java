package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.entity.GuestAccessPolicy;
public record BookingResponse(
        UUID id,
        UUID propertyId,
        UUID guestId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        BigDecimal totalAmount,
        String currency,
        BookingStatus status,
        GuestAccessPolicy guestAccessPolicy,
        String notes,
        String checkoutReminderMessage,
        Instant createdAt,
        Instant updatedAt) {
    public static BookingResponse from(Booking booking) {

        return new BookingResponse(
                booking.getId(),
                booking.getProperty().getId(),
                booking.getGuest() == null ? null : booking.getGuest().getId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus(),
                booking.getGuestAccessPolicy(),
                booking.getNotes(),
                booking.getCheckoutReminderMessage(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}
