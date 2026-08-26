package com.guest_platform.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.entity.AdminRole;
import com.guest_platform.entity.AdminUser;
import com.guest_platform.repository.AdminUserRepository;

@Component
public class AdminBootstrapService implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapService.class);
    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService audit;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrapService(AdminUserRepository admins, PasswordEncoder passwordEncoder, AdminAuditService audit,
            @Value("${app.admin.bootstrap.email:}") String email,
            @Value("${app.admin.bootstrap.password:}") String password,
            @Value("${app.admin.bootstrap.name:}") String name) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (admins.count() > 0) return;
        if (blank(email) && blank(password) && blank(name)) return;
        if (blank(email) || blank(password) || blank(name)) {
            LOGGER.warn("Initial admin bootstrap skipped because configuration is incomplete");
            return;
        }
        if (password.length() < 12 || email.length() > 320 || name.length() > 120 || !email.contains("@")) {
            LOGGER.warn("Initial admin bootstrap skipped because configuration is invalid");
            return;
        }
        AdminUser admin = admins.save(new AdminUser(email.trim().toLowerCase(Locale.ROOT),
                passwordEncoder.encode(password), name.trim(), AdminRole.SUPER_ADMIN));
        audit.record(admin, AdminAuditService.ADMIN_BOOTSTRAPPED);
        LOGGER.info("Initial Hostvero super admin was bootstrapped; remove bootstrap environment variables");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}

