package com.guest_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestLinkEmailRequest(@NotBlank @Size(max = 512) String token) {
}
