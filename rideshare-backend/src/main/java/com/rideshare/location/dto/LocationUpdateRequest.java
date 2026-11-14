package com.rideshare.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * STOMP payload sent by the driver to {@code /app/location/update}.
 * The server verifies that the sender is the driver of {@code rideId}; a different ride id is rejected.
 */
@Schema(description = "Driver location update (WebSocket payload)")
public record LocationUpdateRequest(
        @NotNull UUID rideId,
        @Schema(example = "28.6139") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @Schema(example = "77.2090") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Schema(example = "2026-11-15T10:30:00", description = "Device time in the platform time zone; optional")
        LocalDateTime timestamp) {
}
