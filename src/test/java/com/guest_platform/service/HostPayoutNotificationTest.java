package com.guest_platform.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.guest_platform.entity.HostPayout;
import com.guest_platform.entity.HostPayoutStatus;
import com.guest_platform.repository.HostPayoutRepository;
import com.guest_platform.repository.HostPayoutSettingsRepository;
import com.guest_platform.service.payment.PaystackApiClient;

class HostPayoutNotificationTest {

    @Test
    void transientSubmissionFailureDoesNotCreatePayoutIssueNotification() {
        Fixture fixture = fixture(500);
        fixture.service.reconcilePayouts();
        verify(fixture.notificationService, never()).payoutIssue(any());
        verify(fixture.payout).markFailed("rejected", "Paystack transfer was rejected", true);
    }

    @Test
    void permanentSubmissionRejectionCreatesOnePayoutIssueTransition() {
        Fixture fixture = fixture(400);
        fixture.service.reconcilePayouts();
        verify(fixture.notificationService).payoutIssue(fixture.payout);
        verify(fixture.payout).markFailed("rejected", "Paystack transfer was rejected", false);
    }

    private Fixture fixture(int statusCode) {
        HostPayoutRepository repository = mock(HostPayoutRepository.class);
        HostPayoutSettingsRepository settings = mock(HostPayoutSettingsRepository.class);
        PaystackApiClient paystack = mock(PaystackApiClient.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        HostNotificationService notifications = mock(HostNotificationService.class);
        HostPayout payout = mock(HostPayout.class);
        UUID payoutId = UUID.randomUUID();
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(payout.getId()).thenReturn(payoutId);
        when(payout.getStatus()).thenReturn(HostPayoutStatus.AVAILABLE);
        when(payout.getCurrency()).thenReturn("KES");
        when(payout.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(payout.getProviderReference()).thenReturn("payout-test");
        when(payout.getRecipientCode()).thenReturn("RCP_test");
        when(payout.beginProcessing()).thenReturn(true);
        when(payout.markFailed(any(), any(), anyBoolean())).thenReturn(true);
        when(repository.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenAnswer(invocation -> {
            java.util.Collection<?> statuses = invocation.getArgument(0);
            return statuses.contains(HostPayoutStatus.AVAILABLE) ? List.of(payout) : List.of();
        });
        when(repository.findTop50ByStatusAndRetryableTrueOrderByLastAttemptAtAsc(any())).thenReturn(List.of());
        when(repository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(repository.findForUpdateById(payoutId)).thenReturn(Optional.of(payout));
        when(paystack.hasAvailableBalance("KES", 10000L)).thenReturn(true);
        when(paystack.initiateTransfer(any())).thenThrow(
                new PaystackApiClient.PaystackRequestRejectedException(statusCode, "provider detail"));
        HostPayoutExecutionService service = new HostPayoutExecutionService(repository, settings, paystack,
                transactionManager, "live", 0L, 20, 3, notifications);
        return new Fixture(service, notifications, payout);
    }

    private record Fixture(HostPayoutExecutionService service, HostNotificationService notificationService,
            HostPayout payout) {}
}
