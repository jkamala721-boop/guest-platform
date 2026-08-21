package com.guest_platform.dto;

import java.time.LocalDate;

public record AvailabilityResponse(boolean available, LocalDate checkInDate, LocalDate checkOutDate) {
}
