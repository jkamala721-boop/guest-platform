package com.guest_platform.dto;

public record PaymentWebhookRequest(String providerReference, String eventId, boolean success, String failureReason) {
}
