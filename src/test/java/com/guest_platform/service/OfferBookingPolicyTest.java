package com.guest_platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import com.guest_platform.entity.*;

class OfferBookingPolicyTest {
    private Booking reservation() {
        Property property=mock(Property.class);
        when(property.getCheckInTime()).thenReturn(LocalTime.of(14,0));
        when(property.getCheckOutTime()).thenReturn(LocalTime.of(10,0));
        when(property.getCurrency()).thenReturn("KES");
        Host host=mock(Host.class);
        Booking b=new Booking(host,property);
        b.update(property,LocalDate.of(2027,1,10),LocalDate.of(2027,1,12),new BigDecimal("200"),"KES",BookingStatus.PENDING_PAYMENT,null);
        b.assignGuest(new Guest(host));
        return b;
    }
    @Test void futureReservationOpensExactlyAtT24AndExpiresExactlyAtT12InNairobi() {
        Booking b=reservation();
        Instant in=Instant.parse("2027-01-10T11:00:00Z"); // 14:00 Nairobi
        Instant now=in.minusSeconds(3*86400);
        b.attachOffer(new PropertyOffer(b.getProperty(),"hash",BigDecimal.TEN,now),now.plusSeconds(1800),in.minusSeconds(43200));
        b.confirmOfferReservation(now);
        assertEquals(in,OfferBookingPolicy.checkInAt(b));
        assertFalse(OfferBookingPolicy.canPay(b,in.minusSeconds(86400).minusNanos(1)));
        assertTrue(OfferBookingPolicy.canPay(b,in.minusSeconds(86400)));
        assertTrue(OfferBookingPolicy.canPay(b,in.minusSeconds(43200).minusNanos(1)));
        assertFalse(OfferBookingPolicy.canPay(b,in.minusSeconds(43200)));
        assertTrue(OfferBookingPolicy.expired(b,in.minusSeconds(43200)));
    }
    @Test void registrationWithoutConfirmationStillExpiresAtOriginalHold() {
        Booking b=reservation();Instant now=Instant.parse("2027-01-07T11:00:00Z");
        b.attachOffer(new PropertyOffer(b.getProperty(),"hash",BigDecimal.TEN,now),now.plusSeconds(1800),now.plusSeconds(2*86400));
        assertFalse(OfferBookingPolicy.expired(b,now.plusSeconds(1799)));
        assertTrue(OfferBookingPolicy.expired(b,now.plusSeconds(1800)));
    }
    @Test void paidOfferStayAndLegacyBookingsDoNotExpireAsUnpaidReservations() {
        Booking b=reservation();
        assertEquals(Instant.parse("2027-01-10T14:00:00Z"),OfferBookingPolicy.checkInAt(b));
        assertFalse(OfferBookingPolicy.expired(b,Instant.parse("2030-01-01T00:00:00Z")));
        Instant now=Instant.parse("2027-01-10T10:00:00Z");
        b.attachOffer(new PropertyOffer(b.getProperty(),"hash",BigDecimal.TEN,now),now.plusSeconds(1800),now.plusSeconds(1800));
        b.confirmAfterVerifiedPayment();
        assertFalse(OfferBookingPolicy.expired(b,now.plusSeconds(3600)));
        assertEquals(Instant.parse("2027-01-12T07:00:00Z"),OfferBookingPolicy.checkOutAt(b));
    }
}
