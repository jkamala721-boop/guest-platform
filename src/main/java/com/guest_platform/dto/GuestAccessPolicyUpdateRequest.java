package com.guest_platform.dto;

import com.guest_platform.entity.GuestAccessPolicy;

import jakarta.validation.constraints.NotNull;

public record GuestAccessPolicyUpdateRequest(@NotNull GuestAccessPolicy policy) {
}
