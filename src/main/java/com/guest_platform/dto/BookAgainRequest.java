package com.guest_platform.dto;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
public record BookAgainRequest(@NotNull LocalDate checkInDate, @NotNull LocalDate checkOutDate) { }
