package com.guest_platform.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.PublicReceiptResponse;
import com.guest_platform.dto.ReceiptResponse;
import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.GuestLinkState;
import com.guest_platform.entity.Payment;
import com.guest_platform.entity.Receipt;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.ReceiptRepository;

@Service
public class ReceiptService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ReceiptRepository receiptRepository;
    private final GuestLinkService guestLinkService;

    public ReceiptService(ReceiptRepository receiptRepository, GuestLinkService guestLinkService) {
        this.receiptRepository = receiptRepository;
        this.guestLinkService = guestLinkService;
    }

    @Transactional
    public Receipt createForSucceededPayment(Payment payment) {
        return receiptRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> receiptRepository.save(new Receipt(payment.getHost(), payment.getBooking(), payment,
                        newReceiptNumber())));
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> list(UUID hostId) {
        return receiptRepository.findAllByHostIdOrderByIssuedAtDesc(hostId).stream().map(ReceiptResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(UUID hostId, UUID receiptId) {
        return ReceiptResponse.from(receiptRepository.findByIdAndHostId(receiptId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found")));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getForBooking(UUID hostId, UUID bookingId) {
        return ReceiptResponse.from(receiptRepository.findByBookingIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found")));
    }

    @Transactional(readOnly = true)
    public PublicReceiptResponse getPublic(String token) {
        GuestLink guestLink = guestLinkService.resolveUsableGuestLink(token);
        if (guestLink.getState() != GuestLinkState.STAY_ACTIVE) {
            throw new ResourceNotFoundException("Receipt was not found");
        }
        Receipt receipt = receiptRepository.findByBookingIdAndHostId(guestLink.getBooking().getId(),
                guestLink.getBooking().getHost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found"));
        return PublicReceiptResponse.from(receipt);
    }

    private String newReceiptNumber() {
        byte[] bytes = new byte[12];
        SECURE_RANDOM.nextBytes(bytes);
        return "HV-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
