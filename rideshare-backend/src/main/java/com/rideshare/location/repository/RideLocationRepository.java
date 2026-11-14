package com.rideshare.location.repository;

import com.rideshare.location.entity.RideLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RideLocationRepository extends JpaRepository<RideLocation, UUID> {

    Optional<RideLocation> findFirstByRideIdOrderByRecordedAtDesc(UUID rideId);

    Page<RideLocation> findByRideId(UUID rideId, Pageable pageable);
}
