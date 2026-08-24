package com.guest_platform.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.AuthResponse;
import com.guest_platform.dto.LoginRequest;
import com.guest_platform.dto.RegisterRequest;
import com.guest_platform.service.AuthenticationService;
import com.guest_platform.service.HostSessionService;

import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

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
        return authenticatedResponse(HttpStatus.CREATED, authenticationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticatedResponse(HttpStatus.OK, authenticationService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = tokenFromCookie(request);
        if (token == null && authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7) {
            token = authorization.substring(7);
        }
        if (token != null) hostSessionService.revoke(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                hostSessionService.clearSessionCookie().toString()).build();
    }

    private ResponseEntity<AuthResponse> authenticatedResponse(HttpStatus status,
            AuthenticationService.AuthenticatedSession authenticatedSession) {
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE,
                hostSessionService.sessionCookie(authenticatedSession.sessionToken()).toString())
                .body(authenticatedSession.response());
    }

    private String tokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (hostSessionService.cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }
}
