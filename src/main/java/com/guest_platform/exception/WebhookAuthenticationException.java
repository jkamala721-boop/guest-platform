package com.guest_platform.exception;

public class WebhookAuthenticationException extends RuntimeException {

    public WebhookAuthenticationException() {
        super("Payment webhook authentication failed");
    }
}
