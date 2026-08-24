package com.guest_platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;
import com.guest_platform.entity.Receipt;

public record PublicGuestStayResponse(GuestLinkState state, Instant expiresAt, boolean bookingConfirmed,
        PropertyStayDetails property, StayDetails stay, PaymentDetails payment, ReceiptDetails receipt)
        implements PublicGuestLinkResponse {

    public static PublicGuestStayResponse from(GuestLink guestLink, Receipt receipt, String accessCode) {
        var booking = guestLink.getBooking();
        var property = booking.getProperty();
        var payment = receipt.getPayment();
        return new PublicGuestStayResponse(guestLink.getState(), guestLink.getExpiresAt(), true,
                new PropertyStayDetails(property.getName(), property.getAddress(), property.getMapsUrl(),
                        property.getCheckInTime(), property.getCheckOutTime(), property.getCheckInInstructions(),
                        property.getWifiName(), property.getWifiPassword(), property.getHouseRules(),
                        property.getContactPhone(), property.getAccessMethod(), accessCode,
                        property.getAccessLocationInstructions(), property.getParkingEntryInstructions(),
                        property.getCheckOutInstructions()),
                new StayDetails(booking.getCheckInDate(), booking.getCheckOutDate()),
                new PaymentDetails(payment.getAmount(), payment.getCurrency(), payment.getStatus(), payment.getPaidAt()),
                new ReceiptDetails(true, receipt.getReceiptNumber()));
    }

    public record PropertyStayDetails(String name, String location, String mapsUrl, LocalTime checkInTime,
            LocalTime checkOutTime, String checkInInstructions, String wifiName, String wifiPassword,
            String houseRules, String contactPhone, com.guest_platform.entity.PropertyAccessMethod accessMethod,
            String accessCode, String accessLocationInstructions, String parkingEntryInstructions,
            String checkOutInstructions) {
    }

    public record StayDetails(LocalDate checkInDate, LocalDate checkOutDate) {
    }

    public record PaymentDetails(BigDecimal amount, String currency,
            com.guest_platform.entity.PaymentStatus status, Instant paidAt) {
    }

    public record ReceiptDetails(boolean available, String receiptNumber) {
    }
}
