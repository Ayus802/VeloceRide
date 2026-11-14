package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/** 409 - a unique resource (email, phone, vehicle number, review...) already exists. */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
