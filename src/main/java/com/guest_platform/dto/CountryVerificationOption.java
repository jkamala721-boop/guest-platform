package com.guest_platform.dto;

public record CountryVerificationOption(
        String code,
        String name,
        boolean passportSupported,
        boolean nationalIdSupported) {
}
