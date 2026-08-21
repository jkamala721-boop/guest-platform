package com.guest_platform.dto;
import java.time.LocalDate;
public record UnavailableDateRangeResponse(LocalDate checkInDate, LocalDate checkOutDate) { }
