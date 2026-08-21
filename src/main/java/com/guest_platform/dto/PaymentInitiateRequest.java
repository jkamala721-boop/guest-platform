package com.guest_platform.dto;

import com.guest_platform.entity.PaymentProvider;

import jakarta.validation.constraints.NotNull;

public record PaymentInitiateRequest(@NotNull PaymentProvider provider) {
}
