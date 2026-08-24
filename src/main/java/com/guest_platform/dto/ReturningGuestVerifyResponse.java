package com.guest_platform.dto;
public record ReturningGuestVerifyResponse(boolean verified, Prefill prefill) {
    public record Prefill(String fullName, String phone, String email, String nationality, String idType, String maskedIdentity) { }
}
