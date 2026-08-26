package com.guest_platform.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.guest_platform.dto.ApiErrorResponse;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.InvalidCredentialsException;
import com.guest_platform.exception.GuestLinkExpiredException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.exception.WebhookAuthenticationException;
import com.guest_platform.exception.AdminInvalidCredentialsException;
import com.guest_platform.exception.AdminAccountDisabledException;
import com.guest_platform.exception.LifecycleConflictException;
import com.guest_platform.exception.LifecycleNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.guest_platform.service.payment.PaystackApiClient.PaystackRequestRejectedException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage(),
                        (first, ignored) -> first, java.util.LinkedHashMap::new));
        String field = errors.keySet().stream().findFirst().orElse(null);
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please check the highlighted fields.", field, false, errors);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials(InvalidCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "Incorrect email/phone or password. Check your details and try again.", null, false, null);
    }

    @ExceptionHandler(AdminInvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> adminInvalidCredentials(AdminInvalidCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, "ADMIN_INVALID_CREDENTIALS",
                "Incorrect admin email or password.", null, false, null);
    }

    @ExceptionHandler(AdminAccountDisabledException.class)
    ResponseEntity<ApiErrorResponse> adminDisabled(AdminAccountDisabledException exception) {
        return response(HttpStatus.FORBIDDEN, "ADMIN_ACCOUNT_DISABLED",
                "This admin account is disabled.", null, false, null);
    }

    @ExceptionHandler(LifecycleConflictException.class)
    ResponseEntity<ApiErrorResponse> lifecycleConflict(LifecycleConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), null, false, null);
    }

    @ExceptionHandler(LifecycleNotFoundException.class)
    ResponseEntity<ApiErrorResponse> lifecycleNotFound(LifecycleNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), null, false, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> adminForbidden(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "ADMIN_FORBIDDEN",
                "You do not have permission to perform this admin action.", null, false, null);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(ConflictException exception) {
        String text = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (text.contains("already has a successful payment") || text.contains("paid bookings cannot")) {
            return response(HttpStatus.CONFLICT, "BOOKING_ALREADY_PAID", "This booking has already been paid.", null, false, null);
        }
        if (text.contains("cancelled booking") || text.contains("not awaiting payment") || text.contains("cannot be paid")) {
            return response(HttpStatus.CONFLICT, "BOOKING_NOT_PAYABLE", "This booking is not available for payment.", null, false, null);
        }
        if (text.contains("payment amount") || text.contains("transaction did not match")) {
            return response(HttpStatus.CONFLICT, "PAYMENT_FAILED",
                    "We couldn't verify this payment. Your booking has not been confirmed.", null, false, null);
        }
        if (text.contains("returning guest verification is required")) {
            return response(HttpStatus.CONFLICT, "RETURNING_GUEST_NOT_VERIFIED", "Please verify your previous stay first.", null, false, null);
        }
        if (text.contains("verification code is invalid") || text.contains("verification code is invalid or has expired")) {
            return response(HttpStatus.CONFLICT, "RETURNING_GUEST_OTP_INVALID", "The verification code is invalid or has expired.", null, false, null);
        }
        return response(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), null, false, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException exception) {
        String text = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (text.contains("guest link")) {
            return response(HttpStatus.NOT_FOUND, "GUEST_LINK_INVALID", "This guest link is invalid or no longer active.", null, false, null);
        }
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The requested item was not found.", null, false, null);
    }

    @ExceptionHandler(GuestLinkExpiredException.class)
    ResponseEntity<ApiErrorResponse> expiredGuestLink(GuestLinkExpiredException exception) {
        return response(HttpStatus.NOT_FOUND, "GUEST_LINK_EXPIRED",
                "This guest link has expired. Please contact the host for a new link.", null, false, null);
    }

    @ExceptionHandler(PaystackRequestRejectedException.class)
    ResponseEntity<ApiErrorResponse> paystackRejected(PaystackRequestRejectedException exception) {
        return response(HttpStatus.BAD_REQUEST, "PAYOUT_DESTINATION_INVALID",
                "Your payout destination could not be accepted. Check the details and try again.", null, false, null);
    }

    @ExceptionHandler(WebhookAuthenticationException.class)
    ResponseEntity<ApiErrorResponse> webhookAuthentication(WebhookAuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Please sign in to continue.", null, false, null);
    }

    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class })
    ResponseEntity<ApiErrorResponse> invalidInput(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please check your request and try again.", null, false, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> unavailableProvider(IllegalStateException exception) {
        String text = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (text.contains("payment was not successful")) {
            return response(HttpStatus.CONFLICT, "PAYMENT_FAILED",
                    "The payment could not be completed. Please try again.", null, false, null);
        }
        if (text.contains("paystack") || text.contains("stripe") || text.contains("payment provider")) {
            log.warn("Payment provider unavailable: type={}", exception.getClass().getSimpleName());
            return response(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                    "We couldn't reach the payment service. Your booking has not been charged. Please try again.",
                    null, true, null);
        }
        return internal(exception);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> internal(Exception exception) {
        log.warn("Unhandled API error: type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.", null, false, null);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, String field,
            boolean retryable, Map<String, String> validationErrors) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, field, retryable, validationErrors));
    }
}
