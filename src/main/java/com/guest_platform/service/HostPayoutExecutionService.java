package com.guest_platform.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.guest_platform.entity.HostPayout;
import com.guest_platform.entity.HostPayoutSettings;
import com.guest_platform.entity.HostPayoutStatus;
import com.guest_platform.repository.HostPayoutRepository;
import com.guest_platform.repository.HostPayoutSettingsRepository;
import com.guest_platform.service.payment.PaystackApiClient;

/**
 * Settlement-aware M-Pesa payout worker. It stores PROCESSING before calling
 * Paystack, so a timeout can be reconciled rather than submitted a second time.
 */
@Service
public class HostPayoutExecutionService {
    private static final Logger log = LoggerFactory.getLogger(HostPayoutExecutionService.class);

    private final HostPayoutRepository payoutRepository;
    private final HostPayoutSettingsRepository settingsRepository;
    private final PaystackApiClient paystackApiClient;
    private final TransactionTemplate transactions;
    private final String mode;
    private final Duration settlementHold;
    private final int batchSize;
    private final int maximumAttempts;

    public HostPayoutExecutionService(HostPayoutRepository payoutRepository,
            HostPayoutSettingsRepository settingsRepository, PaystackApiClient paystackApiClient,
            PlatformTransactionManager transactionManager,
            @Value("${app.payments.paystack.mode:mock}") String mode,
            @Value("${app.payouts.settlement-hold-minutes:1440}") long settlementHoldMinutes,
            @Value("${app.payouts.scheduler.batch-size:20}") int batchSize,
            @Value("${app.payouts.maximum-attempts:3}") int maximumAttempts) {
        this.payoutRepository = payoutRepository;
        this.settingsRepository = settingsRepository;
        this.paystackApiClient = paystackApiClient;
        this.transactions = new TransactionTemplate(transactionManager);
        this.mode = mode;
        this.settlementHold = Duration.ofMinutes(Math.max(0, settlementHoldMinutes));
        this.batchSize = Math.max(1, Math.min(batchSize, 50));
        this.maximumAttempts = Math.max(1, maximumAttempts);
    }

    public void reconcilePayouts() {
        List<UUID> pending = payoutRepository.findTop50ByStatusInOrderByCreatedAtAsc(EnumSet.of(HostPayoutStatus.PENDING))
                .stream().limit(batchSize).map(HostPayout::getId).toList();
        pending.forEach(this::releasePending);

        List<UUID> available = payoutRepository.findTop50ByStatusInOrderByCreatedAtAsc(EnumSet.of(HostPayoutStatus.AVAILABLE))
                .stream().limit(batchSize).map(HostPayout::getId).toList();
        available.forEach(this::submitAvailable);

        List<UUID> processing = payoutRepository.findTop50ByStatusInOrderByCreatedAtAsc(EnumSet.of(HostPayoutStatus.PROCESSING))
                .stream().limit(batchSize).map(HostPayout::getId).toList();
        processing.forEach(this::reconcileProcessing);

        List<UUID> retryable = payoutRepository.findTop50ByStatusAndRetryableTrueOrderByLastAttemptAtAsc(HostPayoutStatus.FAILED)
                .stream().filter(value -> value.getAttemptCount() < maximumAttempts).limit(batchSize)
                .map(HostPayout::getId).toList();
        retryable.forEach(this::retryAfterVerification);
    }

    private void releasePending(UUID payoutId) {
        transactions.executeWithoutResult(ignored -> payoutRepository.findForUpdateById(payoutId).ifPresent(payout -> {
            HostPayoutSettings settings = settingsRepository.findByHostId(payout.getHost().getId()).orElse(null);
            payout.releaseIfEligible(settings, Instant.now(), settlementHold);
        }));
    }

    private void submitAvailable(UUID payoutId) {
        HostPayout snapshot = payoutRepository.findById(payoutId).orElse(null);
        if (snapshot == null || snapshot.getStatus() != HostPayoutStatus.AVAILABLE || !fundsAreAvailable(snapshot)) {
            return;
        }
        TransferCommand command = transactions.execute(status -> payoutRepository.findForUpdateById(payoutId)
                .filter(HostPayout::beginProcessing)
                .map(payout -> new TransferCommand(payout.getId(), payout.getProviderReference(), payout.getAmount(),
                        payout.getCurrency(), payout.getRecipientCode()))
                .orElse(null));
        if (command == null) {
            return;
        }
        try {
            PaystackApiClient.TransferResult result = initiateTransfer(command);
            transactions.executeWithoutResult(ignored -> payoutRepository.findForUpdateById(command.payoutId()).ifPresent(
                    payout -> payout.recordTransferAccepted(result.reference(), result.transferCode(), result.status())));
        } catch (PaystackApiClient.PaystackRequestRejectedException exception) {
            boolean retryable = exception.getStatusCode() == 429 || exception.getStatusCode() >= 500;
            failSubmission(command.payoutId(), exception.getProviderMessage(), retryable);
        } catch (RuntimeException exception) {
            // The transfer may have been accepted despite a timeout. Preserve PROCESSING and verify before any retry.
            log.warn("Paystack host payout submission outcome unknown: payoutId={}, exception={}", command.payoutId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void reconcileProcessing(UUID payoutId) {
        HostPayout snapshot = payoutRepository.findById(payoutId).orElse(null);
        if (snapshot == null || snapshot.getStatus() != HostPayoutStatus.PROCESSING) {
            return;
        }
        try {
            PaystackApiClient.TransferResult result = verifyTransfer(snapshot.getProviderReference());
            applyVerifiedProviderState(payoutId, result.reference(), result.transferCode(), result.status());
        } catch (RuntimeException exception) {
            log.warn("Paystack host payout reconciliation deferred: payoutId={}, exception={}", payoutId,
                    exception.getClass().getSimpleName());
        }
    }

    private void retryAfterVerification(UUID payoutId) {
        HostPayout snapshot = payoutRepository.findById(payoutId).orElse(null);
        if (snapshot == null || !snapshot.isRetryable()) {
            return;
        }
        try {
            PaystackApiClient.TransferResult result = verifyTransfer(snapshot.getProviderReference());
            applyVerifiedProviderState(payoutId, result.reference(), result.transferCode(), result.status());
        } catch (PaystackApiClient.PaystackRequestRejectedException exception) {
            // A 404 means the original request was rejected before acceptance; the same reference may be retried.
            if (exception.getStatusCode() == 404) {
                transactions.executeWithoutResult(ignored -> payoutRepository.findForUpdateById(payoutId)
                        .ifPresent(HostPayout::restoreForVerifiedRetry));
            }
        } catch (RuntimeException exception) {
            log.warn("Paystack host payout retry verification deferred: payoutId={}, exception={}", payoutId,
                    exception.getClass().getSimpleName());
        }
    }

    /** Called only after the existing HMAC-SHA512 webhook verification. */
    public void processVerifiedTransferWebhook(String event, String reference, String transferCode) {
        transactions.executeWithoutResult(ignored -> payoutRepository.findForUpdateByProviderReference(reference).ifPresent(payout -> {
            if ("transfer.success".equals(event)) {
                payout.markPaid(reference, transferCode);
            } else if ("transfer.failed".equals(event)) {
                payout.markFailed("failed", "Paystack transfer failed", false);
            } else if ("transfer.reversed".equals(event)) {
                payout.markFailed("reversed", "Paystack transfer was reversed", false);
            }
        }));
    }

    private boolean fundsAreAvailable(HostPayout payout) {
        if ("mock".equalsIgnoreCase(mode)) {
            return true;
        }
        if (!"live".equalsIgnoreCase(mode)) {
            return false;
        }
        try {
            return paystackApiClient.hasAvailableBalance(payout.getCurrency(), toMinorUnits(payout.getAmount()));
        } catch (RuntimeException exception) {
            log.warn("Paystack balance check deferred: payoutId={}, exception={}", payout.getId(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private PaystackApiClient.TransferResult initiateTransfer(TransferCommand command) {
        if ("mock".equalsIgnoreCase(mode)) {
            return new PaystackApiClient.TransferResult(command.reference(), "TRF_MOCK_" + command.payoutId(), "pending");
        }
        return paystackApiClient.initiateTransfer(new PaystackApiClient.TransferRequest("balance",
                toMinorUnits(command.amount()), command.recipientCode(), command.reference(), "Hostvero host payout",
                command.currency()));
    }

    private PaystackApiClient.TransferResult verifyTransfer(String reference) {
        if ("mock".equalsIgnoreCase(mode)) {
            return new PaystackApiClient.TransferResult(reference, null, "pending");
        }
        return paystackApiClient.verifyTransfer(reference);
    }

    private void applyVerifiedProviderState(UUID payoutId, String reference, String transferCode, String status) {
        transactions.executeWithoutResult(ignored -> payoutRepository.findForUpdateById(payoutId).ifPresent(payout -> {
            if ("success".equalsIgnoreCase(status)) {
                payout.markPaid(reference, transferCode);
            } else if ("failed".equalsIgnoreCase(status) || "reversed".equalsIgnoreCase(status)) {
                payout.markFailed(status, "Paystack transfer " + status, false);
            } else {
                payout.updateFromVerification(reference, transferCode, status);
            }
        }));
    }

    private void failSubmission(UUID payoutId, String providerMessage, boolean retryable) {
        transactions.executeWithoutResult(ignored -> payoutRepository.findForUpdateById(payoutId).ifPresent(
                payout -> payout.markFailed("rejected", "Paystack transfer was rejected", retryable)));
        log.warn("Paystack host payout rejected: payoutId={}, retryable={}, message={}", payoutId, retryable,
                providerMessage == null ? "Paystack rejected the request" : providerMessage);
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private record TransferCommand(UUID payoutId, String reference, BigDecimal amount, String currency,
            String recipientCode) {
    }
}
