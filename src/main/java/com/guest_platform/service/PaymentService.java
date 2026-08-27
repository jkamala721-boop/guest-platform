package com.guest_platform.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.PaymentInitiateRequest;
import com.guest_platform.dto.PaymentInitiationResponse;
import com.guest_platform.dto.PaymentResponse;
import com.guest_platform.dto.PaymentWebhookRequest;
import com.guest_platform.entity.Booking;
import com.guest_platform.entity.BookingExtension;
import com.guest_platform.entity.BookingStatus;
import com.guest_platform.entity.GuestLink;
import com.guest_platform.entity.Payment;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.entity.PaymentStatus;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingExtensionRepository;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.PaymentRepository;
import com.guest_platform.service.payment.PaymentProviderAdapter;

@Service
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ReceiptService receiptService;
    private final GuestLinkService guestLinkService;
    private final NotificationService notificationService;
    private final BookingExtensionRepository bookingExtensionRepository;
    private final BookingExtensionService bookingExtensionService;
    private final HostPayoutSettingsService hostPayoutSettingsService;
    private final HostPayoutService hostPayoutService;
    private final HostNotificationService hostNotificationService;
    private final Map<PaymentProvider, PaymentProviderAdapter> providers;
    private final String publicBaseUrl;
    private final HostOperationalAccessService operationalAccess;

    public PaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
            ReceiptService receiptService, GuestLinkService guestLinkService, NotificationService notificationService,
            BookingExtensionRepository bookingExtensionRepository, BookingExtensionService bookingExtensionService,
            HostPayoutSettingsService hostPayoutSettingsService, HostPayoutService hostPayoutService,
            HostNotificationService hostNotificationService,
            List<PaymentProviderAdapter> providerAdapters,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
            HostOperationalAccessService operationalAccess) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.receiptService = receiptService;
        this.guestLinkService = guestLinkService;
        this.notificationService = notificationService;
        this.bookingExtensionRepository = bookingExtensionRepository;
        this.bookingExtensionService = bookingExtensionService;
        this.hostPayoutSettingsService = hostPayoutSettingsService;
        this.hostPayoutService = hostPayoutService;
        this.hostNotificationService = hostNotificationService;
        this.providers = providerAdapters.stream().collect(Collectors.toMap(PaymentProviderAdapter::provider,
                Function.identity(), (first, ignored) -> first, () -> new EnumMap<>(PaymentProvider.class)));
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.operationalAccess = operationalAccess;
    }

    @Transactional
    public PaymentInitiationResponse initiateExtension(UUID hostId, UUID extensionId, PaymentInitiateRequest request) {
        BookingExtension extension = bookingExtensionRepository.findForUpdateByIdAndBookingHostId(extensionId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking extension was not found"));
        return initiateExtension(extension, request);
    }

    @Transactional
    public PaymentInitiationResponse initiateExtension(BookingExtension extension, PaymentInitiateRequest request) {
        if (!extension.isPendingAt(java.time.Instant.now())) {
            throw new ConflictException("Booking extension is not awaiting payment");
        }
        return beginPayment(extension.getBooking(), extension, request, hostReturnUrl());
    }

    @Transactional
    public PaymentInitiationResponse initiate(UUID hostId, UUID bookingId, PaymentInitiateRequest request) {
        operationalAccess.requireAccess(hostId);
        Booking booking = bookingRepository.findForUpdateByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is not awaiting payment");
        }
        return beginPayment(booking, null, request, hostReturnUrl());
    }

    /** Starts payment through a valid public guest link after registration. */
    @Transactional
    public PaymentInitiationResponse initiateForGuestLink(GuestLink guestLink, String guestToken,
            PaymentInitiateRequest request) {
        Booking booking = bookingRepository.findForUpdateById(guestLink.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Guest link was not found"));
        if (booking.getGuest() == null) {
            throw new ConflictException("Guest registration is required before payment");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is not awaiting payment");
        }
        return beginPayment(booking, null, request, publicGuestReturnUrl(guestToken));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForBooking(UUID hostId, UUID bookingId) {
        bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        return paymentRepository.findAllByBookingIdAndHostIdOrderByCreatedAtDesc(bookingId, hostId).stream()
                .map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID hostId, UUID paymentId) {
        return PaymentResponse.from(paymentRepository.findByIdAndHostId(paymentId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found")));
    }

    /** A host-only trusted completion path for a cash payment. */
    @Transactional
    public PaymentResponse confirmCashPayment(UUID hostId, UUID bookingId) {
        Booking booking = bookingRepository.findForUpdateByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Cancelled bookings cannot be paid.");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is not awaiting payment");
        }
        if (paymentRepository.existsByBookingIdAndStatus(booking.getId(), PaymentStatus.SUCCEEDED)) {
            throw new ConflictException("Booking already has a successful payment.");
        }
        if (hasInProgressPayment(booking, null)) {
            throw new ConflictException("A payment is already in progress");
        }

        String reference = "CASH-" + UUID.randomUUID();
        Payment payment = paymentRepository.saveAndFlush(new Payment(booking.getHost(), booking,
                PaymentProvider.CASH, reference, booking.getTotalAmount(), booking.getCurrency()));
        completeVerifiedPayment(payment, "CASH-CONFIRMATION-" + payment.getId());
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse processVerifiedWebhook(PaymentProvider provider, PaymentWebhookRequest request) {
        String providerReference = requireValue(request.providerReference(), "providerReference");
        String eventId = requireValue(request.eventId(), "eventId");
        Payment payment = paymentRepository.findForUpdateByProviderAndProviderReference(provider, providerReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
        if (request.success()) {
            completeVerifiedPayment(payment, eventId);
        } else {
            failVerifiedPayment(payment, eventId, request.failureReason());
        }
        return PaymentResponse.from(payment);
    }

    /**
     * Accepts provider-verified Stripe data only. Stripe-specific signature verification and
     * event parsing stay outside this service; the state transition stays shared with M-Pesa.
     */
    @Transactional
    public PaymentResponse processVerifiedStripeWebhook(StripeWebhookPayment request) {
        Payment payment = paymentRepository.findForUpdateById(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
        if (payment.getProvider() != PaymentProvider.STRIPE || !payment.getBooking().getId().equals(request.bookingId())) {
            throw new ResourceNotFoundException("Payment was not found");
        }
        if (request.providerReference() != null && !request.providerReference().isBlank()
                && !request.providerReference().equals(payment.getProviderReference())) {
            throw new ResourceNotFoundException("Payment was not found");
        }
        String eventId = requireValue(request.eventId(), "eventId");
        if (request.outcome() == StripePaymentOutcome.SUCCEEDED) {
            verifyStripeAmount(payment, request.amountMinor(), request.currency());
            completeVerifiedPayment(payment, eventId);
        } else if (request.outcome() == StripePaymentOutcome.CANCELLED) {
            cancelVerifiedPayment(payment, eventId, request.failureReason());
        } else {
            failVerifiedPayment(payment, eventId, request.failureReason());
        }
        return PaymentResponse.from(payment);
    }

    /** Accepts a signature- and transaction-verified Paystack success only. */
    @Transactional
    public PaymentResponse processVerifiedPaystackWebhook(PaystackWebhookPayment request) {
        Payment payment = paymentRepository.findForUpdateByProviderAndProviderReference(PaymentProvider.PAYSTACK,
                requireValue(request.providerReference(), "providerReference"))
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
        if (!payment.getId().equals(request.paymentId()) || !payment.getBooking().getId().equals(request.bookingId())) {
            throw new ResourceNotFoundException("Payment was not found");
        }
        verifyPaystackAmount(payment, request.amountMinor(), request.currency());
        if (request.processorFeeMinor() != null) {
            payment.recordPaystackSettlement(fromMinorUnits(request.processorFeeMinor()));
        }
        completeVerifiedPayment(payment, requireValue(request.eventId(), "eventId"));
        return PaymentResponse.from(payment);
    }

    private PaymentInitiationResponse beginPayment(Booking booking, BookingExtension extension,
            PaymentInitiateRequest request, String returnUrl) {
        PaymentProviderAdapter provider = providers.get(request.provider());
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported payment provider");
        }
        if (hasInProgressPayment(booking, extension)) {
            throw new ConflictException("A payment is already in progress");
        }
        if (extension == null && paymentRepository.existsByBookingIdAndStatus(booking.getId(), PaymentStatus.SUCCEEDED)) {
            throw new ConflictException("Booking already has a successful payment.");
        }
        BigDecimal bookingAmount = extension == null ? booking.getTotalAmount() : extension.getAdditionalAmount();
        String currency = extension == null ? booking.getCurrency() : extension.getCurrency();
        PaymentAmounts amounts = paymentAmounts(request.provider(), bookingAmount);
        PaystackPayoutDestination paystackDestination = request.provider() == PaymentProvider.PAYSTACK
                ? hostPayoutSettingsService.requireConfiguredPaystackDestination(booking.getHost().getId())
                : null;
        String customerEmail = booking.getGuest() == null ? null : booking.getGuest().getEmail();
        if (request.provider() == PaymentProvider.PAYSTACK && (customerEmail == null || customerEmail.isBlank())) {
            throw new ConflictException("A guest email is required for Paystack payment");
        }
        Payment payment = extension == null
                ? new Payment(booking.getHost(), booking, request.provider(), pendingReference(), amounts.bookingAmount(),
                        amounts.serviceFee(), amounts.chargedAmount(), currency)
                : new Payment(booking.getHost(), booking, extension, request.provider(), pendingReference(),
                        amounts.bookingAmount(), amounts.serviceFee(), amounts.chargedAmount(), currency);
        if (paystackDestination != null) {
            payment.setPaystackPayoutDestination(paystackDestination.method(), paystackDestination.providerReference());
        }
        payment = paymentRepository.saveAndFlush(payment);
        PaymentProviderAdapter.PaymentInitiation initiation = provider.initiate(
                new PaymentProviderAdapter.PaymentInitiationRequest(payment.getId(), booking.getId(),
                        amounts.chargedAmount(), currency, returnUrl, customerEmail,
                        paystackDestination == null ? null : paystackDestination.subaccountCode(),
                        request.provider() == PaymentProvider.PAYSTACK ? amounts.serviceFee() : null));
        payment.setProviderReference(requireValue(initiation.providerReference(), "providerReference"));
        return PaymentInitiationResponse.from(payment, initiation.nextAction());
    }

    /** Shared, transactional completion path for every verified provider success. */
    private void completeVerifiedPayment(Payment payment, String eventId) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }
        Booking booking = bookingRepository.findForUpdateById(payment.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        if (payment.getBookingExtension() == null && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }
        if (!payment.markSucceeded(eventId)) {
            return;
        }
        if (payment.getBookingExtension() != null) {
            if (!bookingExtensionService.applyPaidExtension(payment.getBookingExtension())) {
                throw new ConflictException("Booking extension is no longer available");
            }
        } else if (!booking.confirmAfterVerifiedPayment()) {
            return;
        }
        receiptService.createForSucceededPayment(payment);
        guestLinkService.activateForConfirmedBooking(booking);
        notificationService.reconcileBooking(booking.getId());
        hostPayoutService.queueForVerifiedPayment(payment);
        if (payment.getBookingExtension() == null) {
            hostNotificationService.paymentConfirmed(booking, payment.getId());
        }
    }

    private boolean hasInProgressPayment(Booking booking, BookingExtension extension) {
        EnumSet<PaymentStatus> activeStatuses = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);
        return extension == null
                ? paymentRepository.existsByBookingIdAndStatusIn(booking.getId(), activeStatuses)
                : paymentRepository.existsByBookingExtensionIdAndStatusIn(extension.getId(), activeStatuses);
    }

    private void failVerifiedPayment(Payment payment, String eventId, String failureReason) {
        if (payment.markFailed(eventId, normalizeFailureReason(failureReason)) && payment.getBookingExtension() != null) {
            bookingExtensionService.failExtension(payment.getBookingExtension());
        }
    }

    private void cancelVerifiedPayment(Payment payment, String eventId, String failureReason) {
        if (payment.markCancelled(eventId, normalizeFailureReason(failureReason))
                && payment.getBookingExtension() != null) {
            bookingExtensionService.failExtension(payment.getBookingExtension());
        }
    }

    private void verifyStripeAmount(Payment payment, Long amountMinor, String currency) {
        if (amountMinor == null || currency == null || !payment.getCurrency().equalsIgnoreCase(currency)
                || payment.getAmount().movePointRight(2).longValueExact() != amountMinor.longValue()) {
            throw new ConflictException("Stripe payment amount did not match the booking");
        }
    }

    private void verifyPaystackAmount(Payment payment, Long amountMinor, String currency) {
        if (amountMinor == null || currency == null || !payment.getCurrency().equalsIgnoreCase(currency)
                || payment.getAmount().movePointRight(2).longValueExact() != amountMinor.longValue()) {
            throw new ConflictException("Paystack payment amount did not match the booking");
        }
    }

    private BigDecimal fromMinorUnits(long amountMinor) {
        if (amountMinor < 0) {
            throw new ConflictException("Paystack transaction did not match the booking");
        }
        return BigDecimal.valueOf(amountMinor, 2);
    }

    private PaymentAmounts paymentAmounts(PaymentProvider provider, BigDecimal bookingAmount) {
        if (provider != PaymentProvider.PAYSTACK) {
            return new PaymentAmounts(bookingAmount, BigDecimal.ZERO, bookingAmount);
        }
        BigDecimal serviceFee = bookingAmount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        return new PaymentAmounts(bookingAmount, serviceFee, bookingAmount.add(serviceFee));
    }

    private String pendingReference() {
        return "PENDING-" + UUID.randomUUID();
    }

    private String hostReturnUrl() {
        return publicBaseUrl + "/#payments";
    }

    private String publicGuestReturnUrl(String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            throw new IllegalArgumentException("Guest link token is required");
        }
        return publicBaseUrl + "/guest/" + java.net.URLEncoder.encode(guestToken,
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Hostvero public base URL is required");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeFailureReason(String value) {
        if (value == null || value.isBlank()) {
            return "Payment was declined";
        }
        return value.trim().substring(0, Math.min(value.trim().length(), 500));
    }

    public enum StripePaymentOutcome {
        SUCCEEDED, FAILED, CANCELLED
    }

    public record StripeWebhookPayment(UUID paymentId, UUID bookingId, String providerReference, String eventId,
            StripePaymentOutcome outcome, Long amountMinor, String currency, String failureReason) {
    }

    public record PaystackWebhookPayment(UUID paymentId, UUID bookingId, String providerReference, String eventId,
            Long amountMinor, String currency, Long processorFeeMinor) {
    }

    private record PaymentAmounts(BigDecimal bookingAmount, BigDecimal serviceFee, BigDecimal chargedAmount) {
    }
}
