package com.rideshare.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A driver location as broadcast on /topic/rides/{rideId}/location")
public record LocationResponse(
        UUID rideId,
        @Schema(example = "28.6139") double latitude,
        @Schema(example = "77.2090") double longitude,
        @Schema(description = "When the device recorded the position") Instant timestamp,
        @Schema(description = "When the server accepted the update") Instant receivedAt) {
}
