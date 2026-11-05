package com.guest_platform.security;

import java.util.Locale;

import org.springframework.stereotype.Component;

/** Applies per-account limits without retaining or logging submitted identifiers. */
@Component
public class IdentityRateLimitService {
    private final PublicRateLimiter limiter;

    public IdentityRateLimitService(PublicRateLimiter limiter) {
        this.limiter = limiter;
    }

    public void check(PublicRateLimiter.Category category, String domain, String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String key = PublicRateLimiter.hashIdentity(domain, normalized);
        PublicRateLimiter.Decision decision = limiter.check(category, key);
        if (!decision.allowed()) {
            throw new IdentityRateLimitExceededException(decision.retryAfterSeconds());
        }
    }
}
