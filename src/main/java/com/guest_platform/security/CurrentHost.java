package com.guest_platform.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public final class CurrentHost {

    private CurrentHost() {
    }

    public static UUID id(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof HostPrincipal principal) {
            return principal.hostId();
        }
        throw new AccessDeniedException("Host authentication is required");
    }
}
