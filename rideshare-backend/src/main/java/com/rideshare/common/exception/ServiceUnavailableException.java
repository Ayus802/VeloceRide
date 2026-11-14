package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/** 503 - a required external capability (email/SMS delivery, Google sign-in) is not configured. */
public class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
