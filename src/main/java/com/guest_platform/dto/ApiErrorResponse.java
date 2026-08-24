package com.guest_platform.dto;

import java.util.Map;

/** Stable, safe error contract for browser and API clients. */
public record ApiErrorResponse(String code, String message, String field, boolean retryable,
        Map<String, String> validationErrors) {
}
