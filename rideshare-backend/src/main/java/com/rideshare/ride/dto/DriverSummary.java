package com.rideshare.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Public driver info shown on a ride (no contact details)")
public record DriverSummary(
        UUID id,
        String fullName,
        String profilePicture,
        @Schema(example = "4.8") Double averageRating,
        @Schema(example = "23") long totalReviews) {
}
