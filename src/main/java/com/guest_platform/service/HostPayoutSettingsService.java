package com.guest_platform.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.HostPayoutSettingsResponse;
import com.guest_platform.dto.HostPayoutSettingsUpsertRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostPayoutSettings;
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

    public HostPayoutSettingsService(HostRepository hostRepository, HostPayoutSettingsRepository payoutSettingsRepository,
            PaystackSubaccountService paystackSubaccountService) {
        this.hostRepository = hostRepository;
        this.payoutSettingsRepository = payoutSettingsRepository;
        this.paystackSubaccountService = paystackSubaccountService;
    }

    @Transactional(readOnly = true)
    public HostPayoutSettingsResponse get(UUID hostId) {
        activeHost(hostId);
        return payoutSettingsRepository.findByHostId(hostId).map(HostPayoutSettingsResponse::from)
                .orElseGet(HostPayoutSettingsResponse::notConfigured);
    }

    /**
     * A host row lock makes create-or-update single-writer, preventing duplicate
     * provider subaccounts. Full account numbers are used only for this provider call.
     */
    @Transactional
    public HostPayoutSettingsResponse save(UUID hostId, HostPayoutSettingsUpsertRequest request) {
        Host host = hostRepository.findForUpdateById(hostId).filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
        HostPayoutSettings existing = payoutSettingsRepository.findByHostId(hostId).orElse(null);
        String subaccountCode = paystackSubaccountService.createOrUpdate(host, existing, request);
        String last4 = request.accountNumber().trim().substring(request.accountNumber().trim().length() - 4);
        HostPayoutSettings settings;
        if (existing == null) {
            settings = new HostPayoutSettings(host, request.payoutMethod(), request.settlementBankCode().trim(), last4,
                    request.accountName().trim(), subaccountCode);
        } else {
            existing.update(request.payoutMethod(), request.settlementBankCode().trim(), last4,
                    request.accountName().trim(), subaccountCode);
            settings = existing;
        }
        return HostPayoutSettingsResponse.from(payoutSettingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public String requireConfiguredPaystackSubaccount(UUID hostId) {
        return payoutSettingsRepository.findByHostId(hostId)
                .filter(settings -> settings.getStatus() == PayoutSettingsStatus.CONFIGURED)
                .map(HostPayoutSettings::getPaystackSubaccountCode)
                .filter(code -> code != null && !code.isBlank())
                .orElseThrow(() -> new ConflictException(
                        "The host must configure payout settings before accepting Paystack payments"));
    }

    private Host activeHost(UUID hostId) {
        return hostRepository.findById(hostId).filter(Host::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Host account was not found"));
    }
}
