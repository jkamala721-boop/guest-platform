package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;
import com.guest_platform.entity.PaymentStatus;

public record PublicGuestRegistrationOrPaymentResponse(GuestLinkState state, Instant expiresAt,
        PropertyPreview property, StayPreview stay, PaymentPreview payment) implements PublicGuestLinkResponse {

    public static PublicGuestRegistrationOrPaymentResponse from(GuestLink guestLink, PaymentStatus paymentStatus) {
        return new PublicGuestRegistrationOrPaymentResponse(guestLink.getState(), guestLink.getExpiresAt(),
                new PropertyPreview(guestLink.getBooking().getProperty().getName(),
                        guestLink.getBooking().getProperty().getAddress()),
                new StayPreview(guestLink.getBooking().getCheckInDate(), guestLink.getBooking().getCheckOutDate()),
                new PaymentPreview(guestLink.getBooking().getTotalAmount(), guestLink.getBooking().getCurrency(),
                        paymentStatus));
    }

    public record PropertyPreview(String name, String location) {
    }

    public record StayPreview(LocalDate checkInDate, LocalDate checkOutDate) {
    }

    public record PaymentPreview(BigDecimal amount, String currency, PaymentStatus status) {
    }
}
