package com.guest_platform.dto;

import java.time.Instant;

/** Public host authentication response. The opaque session value is cookie-only. */
public record AuthResponse(Instant expiresAt, HostResponse host) {
}
