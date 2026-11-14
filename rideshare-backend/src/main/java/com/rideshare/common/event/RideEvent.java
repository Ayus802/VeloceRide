package com.rideshare.common.event;

import java.util.List;
import java.util.UUID;

/**
 * Published when a ride starts, completes or is cancelled.
 *
 * @param passengerIds passengers holding accepted (or, after completion, completed) bookings
 */
public record RideEvent(Type type, UUID rideId, UUID driverId, String route, List<UUID> passengerIds) {

    public enum Type {
        STARTED,
        COMPLETED,
        CANCELLED
    }
}
