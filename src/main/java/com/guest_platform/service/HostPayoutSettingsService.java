package com.guest_platform.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.HostPayoutSettingsResponse;
import com.guest_platform.dto.HostPayoutSettingsUpsertRequest;
import com.guest_platform.dto.PaystackBankResponse;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.PayoutMethod;
import com.guest_platform.entity.PayoutSettingsStatus;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.HostPayoutSettingsRepository;
import com.guest_platform.repository.HostRepository;

@Service
public class HostPayoutSettingsService {
    private final HostRepository hostRepository;
    private final HostPayoutSettingsRepository payoutSettingsRepository;
    private final PaystackSubaccountService paystackSubaccountService;
    private final PaystackTransferRecipientService transferRecipientService;
    private final PaystackBankDirectoryService bankDirectoryService;
    private final PayoutDestinationFingerprintService fingerprintService;

    public HostPayoutSettingsService(HostRepository hostRepository, HostPayoutSettingsRepository payoutSettingsRepository,
            PaystackSubaccountService paystackSubaccountService, PaystackTransferRecipientService transferRecipientService,
            PaystackBankDirectoryService bankDirectoryService, PayoutDestinationFingerprintService fingerprintService) {
        this.hostRepository = hostRepository;
        this.payoutSettingsRepository = payoutSettingsRepository;
        this.paystackSubaccountService = paystackSubaccountService;
        this.transferRecipientService = transferRecipientService;
        this.bankDirectoryService = bankDirectoryService;
        this.fingerprintService = fingerprintService;
    }

    @Transactional(readOnly = true)
    public HostPayoutSettingsResponse get(UUID hostId) {
        activeHost(hostId);
        return payoutSettingsRepository.findByHostId(hostId).map(HostPayoutSettingsResponse::from)
                .orElseGet(HostPayoutSettingsResponse::notConfigured);
    }

    @Transactional(readOnly = true)
    public java.util.List<PaystackBankResponse> listKenyanBanks(UUID hostId) {
        activeHost(hostId);
        return bankDirectoryService.listKenyanBanks();
    }

    /** A host row lock prevents duplicate provider destinations on concurrent saves. */
    @Transactional
    public HostPayoutSettingsResponse save(UUID hostId, HostPayoutSettingsUpsertRequest request) {
        Host host = hostRepository.findForUpdateById(hostId).filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
        HostPayoutSettings existing = payoutSettingsRepository.findByHostId(hostId).orElse(null);
        HostPayoutSettings settings = request.payoutMethod() == PayoutMethod.BANK_ACCOUNT
                ? saveBank(host, existing, request) : saveMpesa(host, existing, request);
        return HostPayoutSettingsResponse.from(payoutSettingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public PaystackPayoutDestination requireConfiguredPaystackDestination(UUID hostId) {
        HostPayoutSettings settings = payoutSettingsRepository.findByHostId(hostId)
                .filter(value -> value.getStatus() == PayoutSettingsStatus.CONFIGURED)
                .orElseThrow(() -> new ConflictException(
                        "The host must configure payout settings before accepting Paystack payments"));
        String destinationReference = settings.getPayoutMethod() == PayoutMethod.BANK_ACCOUNT
                ? settings.getPaystackSubaccountCode() : settings.getPaystackRecipientCode();
        if (destinationReference == null || destinationReference.isBlank()) {
            throw new ConflictException("The host must configure payout settings before accepting Paystack payments");
        }
        return new PaystackPayoutDestination(settings.getPayoutMethod(), destinationReference);
    }

    private HostPayoutSettings saveBank(Host host, HostPayoutSettings existing,
            HostPayoutSettingsUpsertRequest request) {
        String bankCode = request.settlementBankCode().trim();
        bankDirectoryService.requireSupportedBank(bankCode);
        String accountNumber = request.accountNumber().trim();
        String subaccountCode = paystackSubaccountService.createOrUpdate(host, existing, request);
        if (existing == null) {
            return new HostPayoutSettings(host, PayoutMethod.BANK_ACCOUNT, bankCode,
                    accountNumber.substring(accountNumber.length() - 4), request.accountName().trim(), subaccountCode,
                    null, null, null);
        }
        existing.update(PayoutMethod.BANK_ACCOUNT, bankCode, accountNumber.substring(accountNumber.length() - 4),
                request.accountName().trim(), subaccountCode, null, null, null);
        return existing;
    }

    private HostPayoutSettings saveMpesa(Host host, HostPayoutSettings existing,
            HostPayoutSettingsUpsertRequest request) {
        String normalizedPhone = normalizeKenyanMpesa(request.mpesaPhone());
        String fingerprint = fingerprintService.fingerprint(normalizedPhone);
        String recipientCode = existing != null && existing.getPayoutMethod() == PayoutMethod.MPESA
                && fingerprintService.matchesExistingFingerprint(normalizedPhone, existing.getMpesaPhoneFingerprint())
                ? existing.getPaystackRecipientCode()
                : transferRecipientService.createIndividualMpesaRecipient(host, normalizedPhone);
        String last4 = normalizedPhone.substring(normalizedPhone.length() - 4);
        if (existing == null) {
            return new HostPayoutSettings(host, PayoutMethod.MPESA, null, null, null, null,
                    recipientCode, last4, fingerprint);
        }
        existing.update(PayoutMethod.MPESA, null, null, null, null, recipientCode, last4, fingerprint);
        return existing;
    }

    private String normalizeKenyanMpesa(String value) {
        String phone = value == null ? "" : value.trim().replaceAll("[\\s()-]", "");
        if (phone.matches("07\\d{8}")) {
            return "+254" + phone.substring(1);
        }
        if (phone.matches("2547\\d{8}")) {
            return "+" + phone;
        }
        if (phone.matches("\\+2547\\d{8}")) {
            return phone;
        }
        throw new IllegalArgumentException("M-Pesa phone number must be a Kenyan mobile number");
    }

    private Host activeHost(UUID hostId) {
        return hostRepository.findById(hostId).filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
    }
}
