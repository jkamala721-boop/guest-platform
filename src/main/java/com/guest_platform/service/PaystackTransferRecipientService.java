package com.guest_platform.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.guest_platform.entity.Host;
import com.guest_platform.service.payment.PaystackApiClient;

/** Provisions the documented Kenyan MPESA transfer-recipient model. */
@Service
public class PaystackTransferRecipientService {
    private static final Logger log = LoggerFactory.getLogger(PaystackTransferRecipientService.class);
    private final String mode;
    private final String secretKey;
    private final PaystackApiClient paystackApiClient;

    public PaystackTransferRecipientService(@Value("${app.payments.paystack.mode:mock}") String mode,
            @Value("${app.payments.paystack.secret-key:}") String secretKey, PaystackApiClient paystackApiClient) {
        this.mode = mode;
        this.secretKey = secretKey;
        this.paystackApiClient = paystackApiClient;
    }

    public String createIndividualMpesaRecipient(Host host, String normalizedPhone) {
        if ("mock".equalsIgnoreCase(mode)) {
            return "RCP_MOCK_" + UUID.randomUUID();
        }
        if (!"live".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Paystack payment mode is invalid");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack integration is not configured");
        }
        try {
            return paystackApiClient.createTransferRecipient(new PaystackApiClient.TransferRecipientRequest(
                    "mobile_money", host.getFullName(), paystackRecipientAccountNumber(normalizedPhone), "MPESA", "KES"));
        } catch (PaystackApiClient.PaystackRequestRejectedException exception) {
            log.warn("Paystack M-Pesa transfer recipient rejected: status={}, message={}",
                    exception.getStatusCode(), exception.getProviderMessage());
            throw new IllegalArgumentException("Paystack rejected the M-Pesa payout destination. Check the number and try again.");
        }
    }

    /** Paystack's Kenya transfer-recipient example uses the local 07XXXXXXXX account-number form. */
    static String paystackRecipientAccountNumber(String canonicalPhone) {
        if (canonicalPhone == null || !canonicalPhone.matches("\\+2547\\d{8}")) {
            throw new IllegalArgumentException("M-Pesa phone number must be a Kenyan mobile number");
        }
        return "0" + canonicalPhone.substring(4);
    }
}
