package com.guest_platform.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.ResourceNotFoundException;
import com.guest_platform.repository.BookingRepository;
import com.guest_platform.repository.PaymentRepository;
import com.guest_platform.repository.BookingExtensionRepository;
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
    private final Map<PaymentProvider, PaymentProviderAdapter> providers;

    public PaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
            ReceiptService receiptService, GuestLinkService guestLinkService, NotificationService notificationService,
            BookingExtensionRepository bookingExtensionRepository, BookingExtensionService bookingExtensionService,
            List<PaymentProviderAdapter> providerAdapters) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.receiptService = receiptService;
        this.guestLinkService = guestLinkService;
        this.notificationService = notificationService;
        this.bookingExtensionRepository = bookingExtensionRepository;
        this.bookingExtensionService = bookingExtensionService;
        this.providers = providerAdapters.stream().collect(Collectors.toMap(PaymentProviderAdapter::provider,
                Function.identity(), (first, ignored) -> first, () -> new EnumMap<>(PaymentProvider.class)));
    }

    @Transactional
    public PaymentInitiationResponse initiateExtension(UUID hostId, UUID extensionId, PaymentInitiateRequest request) {
        BookingExtension extension = bookingExtensionRepository.findByIdAndBookingHostId(extensionId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking extension was not found"));
        return initiateExtension(extension, request);
    }

    @Transactional
    public PaymentInitiationResponse initiateExtension(BookingExtension extension, PaymentInitiateRequest request) {
        if (!extension.isPendingAt(java.time.Instant.now())) throw new ConflictException("Booking extension is not awaiting payment");
        PaymentProviderAdapter provider = providers.get(request.provider());
        if (provider == null) throw new IllegalArgumentException("Unsupported payment provider");
        PaymentProviderAdapter.PaymentInitiation initiation = provider.initiate(extension.getAdditionalAmount(), extension.getCurrency());
        Payment payment = paymentRepository.save(new Payment(extension.getBooking().getHost(), extension.getBooking(), extension,
                request.provider(), initiation.providerReference(), extension.getAdditionalAmount(), extension.getCurrency()));
        return PaymentInitiationResponse.from(payment, initiation.nextAction());
    }

    @Transactional
    public PaymentInitiationResponse initiate(UUID hostId, UUID bookingId, PaymentInitiateRequest request) {
        Booking booking = bookingRepository.findByIdAndHostId(bookingId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found"));
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is not awaiting payment");
        }
        PaymentProviderAdapter provider = providers.get(request.provider());
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported payment provider");
        }
        PaymentProviderAdapter.PaymentInitiation initiation = provider.initiate(booking.getTotalAmount(),
                booking.getCurrency());
        Payment payment = paymentRepository.save(new Payment(booking.getHost(), booking, request.provider(),
                initiation.providerReference(), booking.getTotalAmount(), booking.getCurrency()));
        return PaymentInitiationResponse.from(payment, initiation.nextAction());
    }

    /** Starts payment through a valid public guest link after registration. */
    @Transactional
    public PaymentInitiationResponse initiateForGuestLink(GuestLink guestLink, PaymentInitiateRequest request) {
        Booking booking = guestLink.getBooking();
        if (booking.getGuest() == null) {
            throw new ConflictException("Guest registration is required before payment");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is not awaiting payment");
        }
        PaymentProviderAdapter provider = providers.get(request.provider());
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported payment provider");
        }
        PaymentProviderAdapter.PaymentInitiation initiation = provider.initiate(booking.getTotalAmount(),
                booking.getCurrency());
        Payment payment = paymentRepository.save(new Payment(booking.getHost(), booking, request.provider(),
                initiation.providerReference(), booking.getTotalAmount(), booking.getCurrency()));
        return PaymentInitiationResponse.from(payment, initiation.nextAction());
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

    @Transactional
    public PaymentResponse processVerifiedWebhook(PaymentProvider provider, PaymentWebhookRequest request) {
        String providerReference = requireValue(request.providerReference(), "providerReference");
        String eventId = requireValue(request.eventId(), "eventId");
        Payment payment = paymentRepository.findForUpdateByProviderAndProviderReference(provider, providerReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
        if (request.success()) {
            boolean newlySucceeded = payment.markSucceeded(eventId);
            if (newlySucceeded) {
                if (payment.getBookingExtension() != null) {
                    if (!bookingExtensionService.applyPaidExtension(payment.getBookingExtension())) {
                        throw new ConflictException("Booking extension is no longer available");
                    }
                } else {
                    payment.getBooking().confirmAfterVerifiedPayment();
                }
                receiptService.createForSucceededPayment(payment);
                guestLinkService.activateForConfirmedBooking(payment.getBooking());
                notificationService.reconcileBooking(payment.getBooking().getId());
            }
        } else {
            payment.markFailed(eventId, normalizeFailureReason(request.failureReason()));
            if (payment.getBookingExtension() != null) bookingExtensionService.failExtension(payment.getBookingExtension());
        }
        return PaymentResponse.from(payment);
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
}
