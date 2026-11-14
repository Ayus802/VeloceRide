package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/** 404 - the resource does not exist or is not visible to the caller. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String resource) {
        return new ResourceNotFoundException(resource + " not found");
    }
}
