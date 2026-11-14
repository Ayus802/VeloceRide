package com.rideshare.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Driver location for the REST fallback endpoint (ride id comes from the path)")
public record LocationPoint(
        @Schema(example = "28.6139") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @Schema(example = "77.2090") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Schema(example = "2026-11-15T10:30:00") LocalDateTime timestamp) {
}
