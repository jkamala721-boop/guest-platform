package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.math.RoundingMode;

import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;
import com.guest_platform.entity.GuestAccessPolicy;
import com.guest_platform.entity.PaymentStatus;

public record PublicGuestRegistrationOrPaymentResponse(GuestLinkState state, Instant expiresAt, boolean registrationRequired,
        boolean emailVerified, Instant emailVerificationResendAvailableAt, PropertyPreview property, StayPreview stay,
        PaymentPreview payment, StayAccessDetails stayAccess) implements PublicGuestLinkResponse {

    public static PublicGuestRegistrationOrPaymentResponse from(GuestLink guestLink, PaymentStatus paymentStatus,
            long resendCooldownSeconds) {
        return new PublicGuestRegistrationOrPaymentResponse(guestLink.getState(), guestLink.getExpiresAt(),
                guestLink.getBooking().getGuest() == null,
                guestLink.getBooking().getGuest() != null && guestLink.getBooking().getGuest().isEmailVerified(),
                guestLink.getBooking().getGuest() == null || guestLink.getBooking().getGuest().getEmailVerificationSentAt() == null
                        ? null
                        : guestLink.getBooking().getGuest().getEmailVerificationSentAt().plusSeconds(resendCooldownSeconds),
                new PropertyPreview(guestLink.getBooking().getProperty().getName(),
                        guestLink.getBooking().getProperty().getAddress()),
                new StayPreview(guestLink.getBooking().getCheckInDate(), guestLink.getBooking().getCheckOutDate()),
                paymentPreview(guestLink, paymentStatus),
                stayAccess(guestLink));
    }

    private static PaymentPreview paymentPreview(GuestLink guestLink, PaymentStatus paymentStatus) {
        BigDecimal bookingAmount = guestLink.getBooking().getTotalAmount();
        BigDecimal serviceFee = bookingAmount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        return new PaymentPreview(bookingAmount, serviceFee, bookingAmount.add(serviceFee),
                guestLink.getBooking().getCurrency(), paymentStatus);
    }

    private static StayAccessDetails stayAccess(GuestLink guestLink) {
        var booking = guestLink.getBooking();
        if (booking.getGuest() == null || booking.getGuestAccessPolicy() != GuestAccessPolicy.BEFORE_PAYMENT) {
            return null;
        }
        var property = booking.getProperty();
        return new StayAccessDetails(property.getMapsUrl(), property.getCheckInTime(), property.getCheckOutTime(),
                property.getCheckInInstructions(), property.getWifiName(), property.getWifiPassword(),
                property.getHouseRules(), property.getContactPhone(), property.getHouseNumber(),
                property.getBlockName());
    }

    public record PropertyPreview(String name, String location) {
    }

    public record StayPreview(LocalDate checkInDate, LocalDate checkOutDate) {
    }

    public record PaymentPreview(BigDecimal amount, BigDecimal paystackServiceFee, BigDecimal paystackTotal,
            String currency, PaymentStatus status) {
    }

    /** Present only when the host has explicitly granted pre-payment stay access. */
    public record StayAccessDetails(String mapsUrl, java.time.LocalTime checkInTime, java.time.LocalTime checkOutTime,
            String checkInInstructions, String wifiName, String wifiPassword, String houseRules, String contactPhone,
            String houseNumber, String blockName) {
    }
}
