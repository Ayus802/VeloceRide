package com.rideshare.common.enums;

import java.util.Set;

/**
 * Booking lifecycle:
 * <pre>
 * PENDING  -> ACCEPTED | REJECTED | CANCELLED
 * ACCEPTED -> CANCELLED | COMPLETED
 * REJECTED, CANCELLED, COMPLETED are terminal.
 * </pre>
 * Only ACCEPTED bookings hold seats.
 */
public enum BookingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    COMPLETED;

    public static final Set<BookingStatus> ACTIVE = Set.of(PENDING, ACCEPTED);

    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case PENDING -> Set.of(ACCEPTED, REJECTED, CANCELLED).contains(target);
            case ACCEPTED -> Set.of(CANCELLED, COMPLETED).contains(target);
            case REJECTED, CANCELLED, COMPLETED -> false;
        };
    }
}
