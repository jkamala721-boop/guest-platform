package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import com.guest_platform.entity.Property;
import com.guest_platform.entity.PropertyType;
import com.guest_platform.entity.PropertyAccessMethod;

public record PropertyResponse(UUID id, String name, PropertyType propertyType, String address,
        String mapsUrl, String houseNumber, String blockName, int maxGuests,
        BigDecimal defaultNightlyRate, String currency,
        LocalTime checkInTime, LocalTime checkOutTime, String wifiName, String wifiPassword,
        String houseRules, String checkInInstructions, PropertyAccessMethod accessMethod, String accessCode,
        String accessLocationInstructions, String parkingEntryInstructions, String checkOutInstructions,
        String contactPhone, boolean active,
        Instant createdAt, Instant updatedAt) {
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(property.getId(), property.getName(), property.getPropertyType(),
                property.getAddress(), property.getMapsUrl(), property.getHouseNumber(), property.getBlockName(),
                property.getMaxGuests(),
                property.getDefaultNightlyRate(), property.getCurrency(), property.getCheckInTime(),
                property.getCheckOutTime(), property.getWifiName(), property.getWifiPassword(),
                property.getHouseRules(), property.getCheckInInstructions(), property.getAccessMethod(),
                null, property.getAccessLocationInstructions(), property.getParkingEntryInstructions(),
                property.getCheckOutInstructions(), property.getContactPhone(),
                property.isActive(), property.getCreatedAt(), property.getUpdatedAt());
    }
}
