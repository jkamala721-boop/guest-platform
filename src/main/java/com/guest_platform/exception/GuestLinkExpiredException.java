package com.guest_platform.exception;

/** Indicates that a real guest link has reached its configured expiry time. */
public class GuestLinkExpiredException extends RuntimeException {

    public GuestLinkExpiredException() {
        super("Guest link has expired");
    }
}
