package com.rideshare.common.enums;

/** System-level security role. Driving/booking are NOT roles; every USER can do both. */
public enum Role {
    USER,
    ADMIN
}
