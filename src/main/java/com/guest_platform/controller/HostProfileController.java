package com.guest_platform.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.HostResponse;
import com.guest_platform.dto.UpdateProfileRequest;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.HostProfileService;

import jakarta.validation.Valid;

@RestController
public class HostProfileController {

    private final HostProfileService hostProfileService;

    public HostProfileController(HostProfileService hostProfileService) {
        this.hostProfileService = hostProfileService;
    }

    @GetMapping("/api/me")
    public HostResponse getProfile(Authentication authentication) {
        return hostProfileService.getProfile(CurrentHost.id(authentication));
    }

    @PutMapping("/api/me")
    public HostResponse updateProfile(Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        return hostProfileService.updateProfile(CurrentHost.id(authentication), request);
    }
}
