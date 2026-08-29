package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.guest_platform.entity.BookingStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingUpdateRequest(
        @NotNull UUID propertyId,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal totalAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull BookingStatus status,
        @Size(max = 100) String houseNumber,
        @Size(max = 100) String blockName,
        @Size(max = 2000) String notes,
        @Size(max = 2000) String checkoutReminderMessage) {
}
