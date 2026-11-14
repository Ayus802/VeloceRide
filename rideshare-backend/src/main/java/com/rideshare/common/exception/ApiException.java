package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all expected, client-facing errors. The message is safe to show to users.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
