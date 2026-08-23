package com.guest_platform.service.notification;

import com.guest_platform.entity.Guest;

/** Accepts only international E.164-like numbers; it never guesses a country code. */
final class WhatsAppPhoneNumbers {

    private WhatsAppPhoneNumbers() {
    }

    static String normalize(Guest guest) {
        String source = guest.getWhatsappNumber();
        if (source == null || source.isBlank()) {
            source = guest.getPhone();
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("A guest WhatsApp number is required");
        }

        String value = source.trim();
        if (!value.matches("\\+?[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("Guest WhatsApp number must use international format, for example +254722333444");
        }
        return value.startsWith("+") ? value.substring(1) : value;
    }
}
