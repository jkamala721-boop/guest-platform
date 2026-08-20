package com.guest_platform.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, Instant expiresAt, HostResponse host) {
}
