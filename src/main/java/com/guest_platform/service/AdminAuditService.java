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
    public static final String HOST_VERIFICATION_REVIEW_STARTED = "HOST_VERIFICATION_REVIEW_STARTED";
    public static final String HOST_VERIFICATION_APPROVED = "HOST_VERIFICATION_APPROVED";
    public static final String HOST_VERIFICATION_REJECTED = "HOST_VERIFICATION_REJECTED";
    public static final String HOST_SUSPENDED = "HOST_SUSPENDED";
    public static final String HOST_REACTIVATED = "HOST_REACTIVATED";
    public static final String HOST_AGREEMENT_CREATED = "HOST_AGREEMENT_CREATED";
    public static final String HOST_AGREEMENT_ACTIVATED = "HOST_AGREEMENT_ACTIVATED";
    public static final String HOST_PAYOUT_MANUAL_CONFIRMED = "HOST_PAYOUT_MANUAL_CONFIRMED";
    public static final String HOST_PAYOUT_MARKED_FAILED = "HOST_PAYOUT_MARKED_FAILED";
    public static final String ADMIN_HOST_NOTE_CREATED = "ADMIN_HOST_NOTE_CREATED";
    private static final java.util.Set<String> ALLOWED = java.util.Set.of(ADMIN_LOGIN_SUCCESS, ADMIN_LOGOUT,
            ADMIN_BOOTSTRAPPED, HOST_VERIFICATION_REVIEW_STARTED, HOST_VERIFICATION_APPROVED,
            HOST_VERIFICATION_REJECTED, HOST_SUSPENDED, HOST_REACTIVATED, HOST_AGREEMENT_CREATED,
            HOST_AGREEMENT_ACTIVATED, HOST_PAYOUT_MANUAL_CONFIRMED, HOST_PAYOUT_MARKED_FAILED,
            ADMIN_HOST_NOTE_CREATED);
    private final AdminAuditLogRepository repository;

    public AdminAuditService(AdminAuditLogRepository repository) { this.repository = repository; }

    public void record(AdminUser admin, String action) {
        record(admin, action, "ADMIN_USER", admin.getId().toString(), null);
    }
    public void record(AdminUser admin,String action,String entityType,String entityId,String reason) {
        if (!ALLOWED.contains(action)) throw new IllegalArgumentException("Unsupported admin audit action");
        String safeReason = reason == null ? null : reason.trim().substring(0, Math.min(1000, reason.trim().length()));
        repository.save(new AdminAuditLog(admin,action,entityType,entityId,safeReason));
    }
}
