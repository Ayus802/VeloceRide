package com.rideshare.location.dto;

import com.rideshare.common.enums.RideStatus;

import java.util.UUID;

/** Broadcast on /topic/rides/{rideId}/status when tracking starts or stops. */
public record TrackingStatusMessage(UUID rideId, RideStatus rideStatus, boolean trackingActive) {
}
