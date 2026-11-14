package com.rideshare.location.service;

import com.rideshare.location.dto.LocationResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the most recent location per active ride. The default implementation is in-memory (single instance);
 * for horizontal scaling provide a Redis-backed implementation of this interface (and a broker relay for STOMP).
 */
public interface LatestLocationStore {

    void put(LocationResponse location);

    Optional<LocationResponse> get(UUID rideId);

    void remove(UUID rideId);
}
