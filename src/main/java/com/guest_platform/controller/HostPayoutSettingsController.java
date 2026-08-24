package com.guest_platform.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.HostPayoutSettingsResponse;
import com.guest_platform.dto.HostPayoutSettingsUpsertRequest;
import com.guest_platform.dto.PaystackBankResponse;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.HostPayoutSettingsService;

import jakarta.validation.Valid;

@RestController
public class HostPayoutSettingsController {

    private final HostPayoutSettingsService hostPayoutSettingsService;

    public HostPayoutSettingsController(HostPayoutSettingsService hostPayoutSettingsService) {
        this.hostPayoutSettingsService = hostPayoutSettingsService;
    }

    @GetMapping("/api/me/payout-settings")
    public HostPayoutSettingsResponse get(Authentication authentication) {
        return hostPayoutSettingsService.get(CurrentHost.id(authentication));
    }

    @GetMapping("/api/me/payout-settings/banks")
    public java.util.List<PaystackBankResponse> banks(Authentication authentication) {
        return hostPayoutSettingsService.listKenyanBanks(CurrentHost.id(authentication));
    }

    @PutMapping("/api/me/payout-settings")
    public HostPayoutSettingsResponse save(Authentication authentication,
            @Valid @RequestBody HostPayoutSettingsUpsertRequest request) {
        return hostPayoutSettingsService.save(CurrentHost.id(authentication), request);
    }
}
