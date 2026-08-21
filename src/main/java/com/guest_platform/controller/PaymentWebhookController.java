package com.guest_platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.PaymentWebhookRequest;
import com.guest_platform.entity.PaymentProvider;
import com.guest_platform.service.PaymentService;
import com.guest_platform.service.PaymentWebhookVerifier;

@RestController
@RequestMapping("/api/webhooks")
public class PaymentWebhookController {

    private final PaymentWebhookVerifier paymentWebhookVerifier;
    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentWebhookVerifier paymentWebhookVerifier, PaymentService paymentService) {
        this.paymentWebhookVerifier = paymentWebhookVerifier;
        this.paymentService = paymentService;
    }

    @PostMapping("/mpesa")
    public ResponseEntity<Void> mpesa(@RequestHeader(value = "X-Mpesa-Webhook-Secret", required = false) String secret,
            @RequestBody String payload) {
        PaymentWebhookRequest request = paymentWebhookVerifier.verifyMpesa(secret, payload);
        paymentService.processVerifiedWebhook(PaymentProvider.MPESA, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(@RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {
        PaymentWebhookRequest request = paymentWebhookVerifier.verifyStripe(signature, payload);
        paymentService.processVerifiedWebhook(PaymentProvider.STRIPE, request);
        return ResponseEntity.noContent().build();
    }
}
