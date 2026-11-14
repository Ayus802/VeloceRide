package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/** 422 - a business rule rejected the request (e.g. not enough seats). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
