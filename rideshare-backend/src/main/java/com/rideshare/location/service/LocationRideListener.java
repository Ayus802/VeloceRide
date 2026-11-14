package com.rideshare.location.service;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.event.RideEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Starts/stops tracking in response to ride lifecycle events. */
@Component
@RequiredArgsConstructor
public class LocationRideListener {

    private final LocationService locationService;

    @EventListener
    public void onRideEvent(RideEvent event) {
        RideStatus status = switch (event.type()) {
            case STARTED -> RideStatus.ONGOING;
            case COMPLETED -> RideStatus.COMPLETED;
            case CANCELLED -> RideStatus.CANCELLED;
        };
        locationService.onRideStatusChanged(event.rideId(), status);
    }
}
