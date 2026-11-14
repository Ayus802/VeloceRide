package com.rideshare.location.service;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.response.PageResponse;
import com.rideshare.location.dto.LocationResponse;
import com.rideshare.location.dto.LocationUpdateRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LocationService {

    /** Accepts a location update from the ride's driver while the ride is ONGOING, then broadcasts it. */
    LocationResponse publishUpdate(UUID driverId, LocationUpdateRequest request);

    /** May this user subscribe to the ride's live topics? (driver, or passenger with an ACCEPTED booking) */
    boolean canSubscribe(UUID userId, UUID rideId);

    LocationResponse getLatest(UUID userId, UUID rideId);

    PageResponse<LocationResponse> getHistory(UUID userId, UUID rideId, Pageable pageable);

    /** Broadcasts tracking start/stop and clears state when a ride ends. */
    void onRideStatusChanged(UUID rideId, RideStatus status);
}
