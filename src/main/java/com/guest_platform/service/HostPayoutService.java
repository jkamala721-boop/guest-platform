package com.guest_platform.service;

import org.springframework.stereotype.Service;

import com.guest_platform.entity.HostPayout;
import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.entity.PayoutMethod;
import com.guest_platform.repository.HostPayoutRepository;

/** Keeps downstream M-Pesa transfer obligations out of the guest payment state machine. */
@Service
public class HostPayoutService {
    private final HostPayoutRepository hostPayoutRepository;

    public HostPayoutService(HostPayoutRepository hostPayoutRepository) {
        this.hostPayoutRepository = hostPayoutRepository;
    }

    public void queueForVerifiedPayment(Payment payment) {
        if (payment.getProvider() != PaymentProvider.PAYSTACK
                || payment.getPayoutMethod() != PayoutMethod.MPESA
                || payment.getPayoutDestinationReference() == null
                || payment.getPayoutDestinationReference().isBlank()
                || hostPayoutRepository.findByPaymentId(payment.getId()).isPresent()) {
            return;
        }
        hostPayoutRepository.save(new HostPayout(payment, payment.getPayoutDestinationReference()));
    }
}
