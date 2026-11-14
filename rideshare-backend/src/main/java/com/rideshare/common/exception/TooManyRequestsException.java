package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/** 429 - the caller is sending requests too frequently. */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
