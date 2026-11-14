package com.rideshare.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Aggregate rating of a user (hidden reviews excluded)")
public record UserRatingResponse(
        UUID userId,
        @Schema(example = "4.67", description = "null when the user has no reviews yet") Double averageRating,
        @Schema(example = "12") long totalReviews) {

    public static UserRatingResponse empty(UUID userId) {
        return new UserRatingResponse(userId, null, 0);
    }
}
