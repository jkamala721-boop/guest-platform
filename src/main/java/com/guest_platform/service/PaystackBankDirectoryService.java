package com.guest_platform.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.guest_platform.dto.PaystackBankResponse;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.service.payment.PaystackApiClient;

/** Reads the Kenya bank directory from Paystack only in live mode. */
@Service
public class PaystackBankDirectoryService {
    private static final List<PaystackBankResponse> MOCK_BANKS = List.of(
            new PaystackBankResponse("KEPSS-TEST", "Test Kenya Bank"));

    private final String mode;
    private final PaystackApiClient paystackApiClient;

    public PaystackBankDirectoryService(@Value("${app.payments.paystack.mode:mock}") String mode,
            PaystackApiClient paystackApiClient) {
        this.mode = mode;
        this.paystackApiClient = paystackApiClient;
    }

    public List<PaystackBankResponse> listKenyanBanks() {
        if ("mock".equalsIgnoreCase(mode)) {
            return MOCK_BANKS;
        }
        if (!"live".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Paystack payment mode is invalid");
        }
        return paystackApiClient.listKenyanBanks().stream()
                .map(bank -> new PaystackBankResponse(bank.code(), bank.name())).toList();
    }

    public void requireSupportedBank(String code) {
        boolean supported = listKenyanBanks().stream().anyMatch(bank -> bank.code().equals(code));
        if (!supported) {
            throw new ConflictException("The selected bank is not supported by Paystack Kenya");
        }
    }
}
