package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.guest_platform.entity.PropertyType;
import com.guest_platform.entity.PropertyAccessMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PropertyUpsertRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull PropertyType propertyType,
        @NotBlank @Size(max = 500) String address,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "mapsUrl must be an HTTP(S) URL") String mapsUrl,
        @Size(max = 100) String houseNumber,
        @Size(max = 100) String blockName,
        @NotNull @Min(1) @Max(10000) Integer maxGuests,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal defaultNightlyRate,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull LocalTime checkInTime,
        @NotNull LocalTime checkOutTime,
        @Size(max = 100) String wifiName,
        @Size(max = 200) String wifiPassword,
        @Size(max = 5000) String houseRules,
        @Size(max = 5000) String checkInInstructions,
        PropertyAccessMethod accessMethod,
        @Size(max = 200) String accessCode,
        @Size(max = 5000) String accessLocationInstructions,
        @Size(max = 5000) String parkingEntryInstructions,
        @Size(max = 5000) String checkOutInstructions,
        @Size(max = 32) String contactPhone,
        @NotNull Boolean active) {
}
