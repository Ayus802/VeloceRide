package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/** 403 - the caller is authenticated but does not own / may not access the resource. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
