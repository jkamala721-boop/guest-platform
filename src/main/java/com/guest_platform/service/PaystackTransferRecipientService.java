package com.guest_platform.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.guest_platform.entity.Host;
import com.guest_platform.service.payment.PaystackApiClient;

/** Provisions the documented Kenyan MPESA transfer-recipient model. */
@Service
public class PaystackTransferRecipientService {
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
        return paystackApiClient.createTransferRecipient(new PaystackApiClient.TransferRecipientRequest(
                "mobile_money", host.getFullName(), normalizedPhone, "MPESA", "KES"));
    }
}
