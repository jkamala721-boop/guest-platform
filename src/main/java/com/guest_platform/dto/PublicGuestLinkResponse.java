package com.guest_platform.dto;

/**
 * A state-specific public guest-link response. Implementations intentionally do
 * not expose internal identifiers or guest PII.
 */
public sealed interface PublicGuestLinkResponse permits PublicGuestRegistrationOrPaymentResponse,
        PublicGuestStayResponse {
}
