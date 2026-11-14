package com.rideshare.location.service;

import com.rideshare.location.dto.LocationResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryLatestLocationStore implements LatestLocationStore {

    private final Map<UUID, LocationResponse> latest = new ConcurrentHashMap<>();

    @Override
    public void put(LocationResponse location) {
        // Keep the newest by device timestamp even if updates arrive out of order.
        latest.merge(location.rideId(), location,
                (old, candidate) -> candidate.timestamp().isBefore(old.timestamp()) ? old : candidate);
    }

    @Override
    public Optional<LocationResponse> get(UUID rideId) {
        return Optional.ofNullable(latest.get(rideId));
    }

    @Override
    public void remove(UUID rideId) {
        latest.remove(rideId);
    }
}
