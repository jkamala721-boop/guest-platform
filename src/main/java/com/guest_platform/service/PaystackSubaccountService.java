package com.guest_platform.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.guest_platform.dto.HostPayoutSettingsUpsertRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.PayoutMethod;
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

    public PaystackApiClient.Subaccount createOrUpdate(Host host, HostPayoutSettings existing,
            HostPayoutSettingsUpsertRequest request) {
        if ("mock".equalsIgnoreCase(mode)) {
            String code = canReuseMock(existing) ? existing.getPaystackSubaccountCode() : "ACCT_MOCK_" + host.getId();
            return new PaystackApiClient.Subaccount(code, existing == null ? null : existing.getPaystackSubaccountId(),
                    "mock", true, true);
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
        String reusableCode = reconcileExistingCode(existing);
        return reusableCode == null ? paystackApiClient.createSubaccount(payload)
                : paystackApiClient.updateSubaccount(reusableCode, payload);
    }

    private String reconcileExistingCode(HostPayoutSettings existing) {
        if (existing == null || existing.getPayoutMethod() != PayoutMethod.BANK_ACCOUNT
                || existing.getPaystackSubaccountCode() == null || existing.getPaystackSubaccountCode().isBlank()) {
            return null;
        }
        if (!existing.getPaystackSubaccountCode().startsWith("ACCT_")
                || existing.getPaystackSubaccountCode().startsWith("ACCT_MOCK_")) {
            return null;
        }
        String storedDomain = existing.getPaystackSubaccountDomain();
        if (storedDomain != null && !storedDomain.isBlank() && !isCompatibleWithConfiguredCredentials(existing)) {
            return null;
        }
        try {
            PaystackApiClient.Subaccount fetched = paystackApiClient.fetchSubaccount(existing.getPaystackSubaccountCode());
            return existing.getPaystackSubaccountCode().equals(fetched.code()) && domainMatchesCredentials(fetched.domain())
                    ? fetched.code() : null;
        } catch (PaystackApiClient.PaystackRequestRejectedException exception) {
            if (exception.getStatusCode() == 404) {
                return null;
            }
            throw exception;
        }
    }

    private boolean canReuseMock(HostPayoutSettings existing) {
        return existing != null && existing.getPayoutMethod() == PayoutMethod.BANK_ACCOUNT
                && existing.getPaystackSubaccountCode() != null && !existing.getPaystackSubaccountCode().isBlank()
                && "mock".equalsIgnoreCase(existing.getPaystackSubaccountDomain());
    }

    private boolean domainMatchesCredentials(String domain) {
        if (domain == null || domain.isBlank()) return false;
        if (secretKey.startsWith("sk_test_")) return "test".equalsIgnoreCase(domain);
        if (secretKey.startsWith("sk_live_")) return "live".equalsIgnoreCase(domain);
        return false;
    }

    public boolean isCompatibleWithConfiguredCredentials(HostPayoutSettings settings) {
        String domain = settings.getPaystackSubaccountDomain();
        if (domain == null || domain.isBlank()) {
            return false;
        }
        if ("mock".equalsIgnoreCase(mode)) {
            return "mock".equalsIgnoreCase(domain);
        }
        if (secretKey.startsWith("sk_test_")) {
            return "test".equalsIgnoreCase(domain);
        }
        if (secretKey.startsWith("sk_live_")) {
            return "live".equalsIgnoreCase(domain);
        }
        return false;
    }
}
