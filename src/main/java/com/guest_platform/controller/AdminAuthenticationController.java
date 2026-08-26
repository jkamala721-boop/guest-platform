package com.guest_platform.controller;

import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.AdminLoginRequest;
import com.guest_platform.dto.AdminMeResponse;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.AdminUserRepository;
import com.guest_platform.security.AdminPrincipal;
import com.guest_platform.service.AdminAuditService;
import com.guest_platform.service.AdminAuthenticationService;
import com.guest_platform.service.AdminSessionService;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthenticationController {
    private final AdminAuthenticationService authenticationService;
    private final AdminSessionService sessions;
    private final AdminUserRepository admins;
    private final AdminAuditService audit;

    public AdminAuthenticationController(AdminAuthenticationService authenticationService,
            AdminSessionService sessions, AdminUserRepository admins, AdminAuditService audit) {
        this.authenticationService = authenticationService;
        this.sessions = sessions;
        this.admins = admins;
        this.audit = audit;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AdminMeResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminAuthenticationService.AuthenticatedAdmin result = authenticationService.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, sessions.sessionCookie(result.token()).toString())
                .body(result.response());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = token(request);
        if (token != null) sessions.revoke(token).ifPresent(admin -> audit.record(admin, AdminAuditService.ADMIN_LOGOUT));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clearCookie().toString()).build();
    }

    @GetMapping("/me")
    public AdminMeResponse me(Authentication authentication) {
        UUID id = ((AdminPrincipal) authentication.getPrincipal()).id();
        return admins.findById(id).map(AdminMeResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account was not found"));
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (sessions.cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }
}
