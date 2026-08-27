package com.guest_platform.exception;

import org.springframework.http.HttpStatus;
import com.guest_platform.dto.HostOperationalAccessResponse;

public class HostOperationalAccessException extends RuntimeException {
    private final HostOperationalAccessResponse access;
    private final HttpStatus status;

    public HostOperationalAccessException(HostOperationalAccessResponse access, HttpStatus status) {
        super(access.message());
        this.access = access;
        this.status = status;
    }

    public HostOperationalAccessResponse getAccess() { return access; }
    public HttpStatus getStatus() { return status; }
}
