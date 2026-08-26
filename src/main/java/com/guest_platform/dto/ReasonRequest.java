package com.guest_platform.dto;
import jakarta.validation.constraints.*; public record ReasonRequest(@NotBlank @Size(max=1000) String reason) {}
