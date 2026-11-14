package com.rideshare.common.validation;

/**
 * Shared validation regular expressions and messages.
 */
public final class ValidationPatterns {

    /** E.164-style phone number: optional '+', 8 to 15 digits, no leading zero. */
    public static final String PHONE = "^\\+?[1-9]\\d{7,14}$";
    public static final String PHONE_MESSAGE = "must be a valid phone number with 8-15 digits, optionally prefixed with '+'";

    /** At least 8 chars with an upper-case letter, a lower-case letter, a digit and a special character. */
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,64}$";
    public static final String PASSWORD_MESSAGE =
            "must be 8-64 characters and contain upper-case, lower-case, digit and special characters";

    /** Vehicle registration number after normalisation (upper-case, no spaces/dashes). */
    public static final String VEHICLE_NUMBER = "^[A-Za-z0-9 -]{4,20}$";

    private ValidationPatterns() {
    }
}
