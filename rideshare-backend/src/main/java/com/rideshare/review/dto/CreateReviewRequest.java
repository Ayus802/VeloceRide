package com.rideshare.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Review a co-traveller after a COMPLETED ride. The reviewer is always the authenticated user.")
public record CreateReviewRequest(
        @NotNull UUID rideId,
        @Schema(description = "Driver reviews a passenger, or a passenger reviews the driver") @NotNull UUID reviewedUserId,
        @Schema(example = "5") @NotNull @Min(1) @Max(5) Integer rating,
        @Schema(example = "Punctual and friendly, smooth drive.") @Size(max = 1000) String comment) {
}
