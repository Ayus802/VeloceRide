package com.rideshare.common.event;

import java.util.UUID;

/**
 * Published (synchronously, inside the business transaction) whenever a booking changes state.
 * Listeners such as notifications and payments react without the booking module depending on them.
 */
public record BookingEvent(
        Type type,
        UUID bookingId,
        String bookingReference,
        UUID rideId,
        UUID passengerId,
        String passengerName,
        UUID driverId,
        String route,
        int numberOfSeats) {

    public enum Type {
        REQUESTED,
        ACCEPTED,
        REJECTED,
        /** Pending request rejected automatically because the ride started. */
        AUTO_REJECTED,
        CANCELLED_BY_PASSENGER,
        /** Booking cancelled because the driver cancelled the whole ride. */
        CANCELLED_BY_RIDE,
        COMPLETED
    }

    public boolean isCancellation() {
        return type == Type.CANCELLED_BY_PASSENGER || type == Type.CANCELLED_BY_RIDE;
    }
}
