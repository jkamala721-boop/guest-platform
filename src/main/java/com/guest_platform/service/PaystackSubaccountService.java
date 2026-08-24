package com.guest_platform.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.guest_platform.dto.HostPayoutSettingsUpsertRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.service.payment.PaystackApiClient;

/** Isolates Paystack subaccount provisioning from host settings and payment completion. */
@Service
public class PaystackSubaccountService {
    /** Required by Paystack at setup; each transaction's flat charge overrides this default. */
    static final BigDecimal NEUTRAL_PERCENTAGE_CHARGE = BigDecimal.ZERO;

    private final String mode;
    private final String secretKey;
    private final PaystackApiClient paystackApiClient;

    public PaystackSubaccountService(@Value("${app.payments.paystack.mode:mock}") String mode,
            @Value("${app.payments.paystack.secret-key:}") String secretKey, PaystackApiClient paystackApiClient) {
        this.mode = mode;
        this.secretKey = secretKey;
        this.paystackApiClient = paystackApiClient;
    }

    public String createOrUpdate(Host host, HostPayoutSettings existing, HostPayoutSettingsUpsertRequest request) {
        if ("mock".equalsIgnoreCase(mode)) {
            return existing == null || existing.getPaystackSubaccountCode() == null
                    ? "ACCT_MOCK_" + UUID.randomUUID() : existing.getPaystackSubaccountCode();
        }
        if (!"live".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Paystack payment mode is invalid");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack integration is not configured");
        }
        PaystackApiClient.SubaccountRequest payload = new PaystackApiClient.SubaccountRequest(
                host.getFullName(), request.settlementBankCode().trim(), request.accountNumber().trim(),
                NEUTRAL_PERCENTAGE_CHARGE, "Hostvero host payout destination");
        return existing == null || existing.getPaystackSubaccountCode() == null
                ? paystackApiClient.createSubaccount(payload)
                : paystackApiClient.updateSubaccount(existing.getPaystackSubaccountCode(), payload);
    }
}
