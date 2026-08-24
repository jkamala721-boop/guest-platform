package com.guest_platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Low-frequency reconciliation keeps external transfer calls out of guest payment webhooks. */
@Component
public class HostPayoutScheduler {
    private final HostPayoutExecutionService hostPayoutExecutionService;
    private final boolean enabled;

    public HostPayoutScheduler(HostPayoutExecutionService hostPayoutExecutionService,
            @Value("${app.payouts.scheduler.enabled:true}") boolean enabled) {
        this.hostPayoutExecutionService = hostPayoutExecutionService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.payouts.scheduler.fixed-delay-ms:300000}")
    public void reconcile() {
        if (enabled) {
            hostPayoutExecutionService.reconcilePayouts();
        }
    }
}
