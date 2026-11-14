package com.guest_platform.security;

public class IdentityRateLimitExceededException extends RateLimitExceededException {
    public IdentityRateLimitExceededException(long retryAfterSeconds) {
        super(retryAfterSeconds);
    }
}
