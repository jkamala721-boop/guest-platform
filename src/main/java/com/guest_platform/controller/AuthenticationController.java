package com.guest_platform.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.AuthResponse;
import com.guest_platform.dto.LoginRequest;
import com.guest_platform.dto.RegisterRequest;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.AuthenticationService;
import com.guest_platform.service.HostSessionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final HostSessionService hostSessionService;

    public AuthenticationController(AuthenticationService authenticationService,
            HostSessionService hostSessionService) {
        this.authenticationService = authenticationService;
        this.hostSessionService = hostSessionService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        CurrentHost.id(authentication);
        hostSessionService.revoke(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }
}
