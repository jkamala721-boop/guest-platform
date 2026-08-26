package com.guest_platform.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.AdminLoginRequest;
import com.guest_platform.dto.AdminMeResponse;
import com.guest_platform.entity.AdminStatus;
import com.guest_platform.entity.AdminUser;
import com.guest_platform.exception.AdminAccountDisabledException;
import com.guest_platform.exception.AdminInvalidCredentialsException;
import com.guest_platform.repository.AdminUserRepository;

@Service
public class AdminAuthenticationService {
    private final AdminUserRepository admins;
    private final AdminSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService audit;

    public AdminAuthenticationService(AdminUserRepository admins, AdminSessionService sessions,
            PasswordEncoder passwordEncoder, AdminAuditService audit) {
        this.admins = admins;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public AuthenticatedAdmin login(AdminLoginRequest request) {
        AdminUser admin = admins.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(AdminInvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new AdminInvalidCredentialsException();
        }
        if (admin.getStatus() != AdminStatus.ACTIVE) throw new AdminAccountDisabledException();
        Instant now = Instant.now();
        admin.recordLogin(now);
        AdminSessionService.SessionToken token = sessions.create(admin);
        audit.record(admin, AdminAuditService.ADMIN_LOGIN_SUCCESS);
        return new AuthenticatedAdmin(token, AdminMeResponse.from(admin));
    }

    private String normalizeEmail(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    public record AuthenticatedAdmin(AdminSessionService.SessionToken token, AdminMeResponse response) {}
}

