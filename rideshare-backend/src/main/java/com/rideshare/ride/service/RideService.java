package com.rideshare.ride.service;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.response.PageResponse;
import com.rideshare.ride.dto.RideRequest;
import com.rideshare.ride.dto.RideResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface RideService {

    RideResponse publish(UUID driverId, RideRequest request);

    RideResponse getRide(UUID rideId);

    RideResponse update(UUID driverId, UUID rideId, RideRequest request);

    /** Cancels a scheduled ride and every pending/accepted booking on it. */
    RideResponse cancel(UUID driverId, UUID rideId);

    RideResponse start(UUID driverId, UUID rideId);

    RideResponse complete(UUID driverId, UUID rideId);

    PageResponse<RideResponse> getMyRides(UUID driverId, RideStatus status, Pageable pageable);

    PageResponse<RideResponse> listUpcoming(Pageable pageable);

    PageResponse<RideResponse> search(String source, String destination, LocalDate date, Pageable pageable);

    /** Admin listing. */
    PageResponse<RideResponse> listAll(RideStatus status, Pageable pageable);
}
