package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 - correct credentials, but the email and/or phone number are not verified yet.
 * The response body's {@code data} carries the verification challenge so the client can continue
 * straight to code entry (see GlobalExceptionHandler).
 */
public class VerificationRequiredException extends ApiException {

    private final transient Object challenge;

    public VerificationRequiredException(Object challenge) {
        super(HttpStatus.FORBIDDEN, "Verify your email address and phone number to continue");
        this.challenge = challenge;
    }

    public Object getChallenge() {
        return challenge;
    }
}
