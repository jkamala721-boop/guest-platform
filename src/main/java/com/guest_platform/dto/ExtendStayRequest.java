package com.guest_platform.dto;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
public record ExtendStayRequest(@NotNull LocalDate newCheckOutDate) { }
