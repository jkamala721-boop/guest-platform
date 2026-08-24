package com.guest_platform.dto;

/** A safe, human-readable bank option sourced from Paystack's List Banks API. */
public record PaystackBankResponse(String code, String name) {
}
