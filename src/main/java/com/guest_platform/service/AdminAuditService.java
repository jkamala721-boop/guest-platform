package com.guest_platform.service;

import org.springframework.stereotype.Service;
import com.guest_platform.entity.AdminAuditLog;
import com.guest_platform.entity.AdminUser;
import com.guest_platform.repository.AdminAuditLogRepository;

/** Append-only audit writer. This API intentionally accepts no arbitrary state or metadata in Batch 1. */
@Service
public class AdminAuditService {
    public static final String ADMIN_LOGIN_SUCCESS = "ADMIN_LOGIN_SUCCESS";
    public static final String ADMIN_LOGOUT = "ADMIN_LOGOUT";
    public static final String ADMIN_BOOTSTRAPPED = "ADMIN_BOOTSTRAPPED";
    private final AdminAuditLogRepository repository;

    public AdminAuditService(AdminAuditLogRepository repository) { this.repository = repository; }

    public void record(AdminUser admin, String action) {
        if (!java.util.Set.of(ADMIN_LOGIN_SUCCESS, ADMIN_LOGOUT, ADMIN_BOOTSTRAPPED).contains(action)) {
            throw new IllegalArgumentException("Unsupported admin audit action");
        }
        repository.save(new AdminAuditLog(admin, action, "ADMIN_USER", admin.getId().toString()));
    }
}

