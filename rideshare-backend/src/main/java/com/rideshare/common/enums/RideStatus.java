package com.rideshare.common.enums;

import java.util.Set;

/**
 * Ride lifecycle: SCHEDULED -> ONGOING -> COMPLETED, or SCHEDULED -> CANCELLED.
 */
public enum RideStatus {
    SCHEDULED,
    ONGOING,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(RideStatus target) {
        return switch (this) {
            case SCHEDULED -> Set.of(ONGOING, CANCELLED).contains(target);
            case ONGOING -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
