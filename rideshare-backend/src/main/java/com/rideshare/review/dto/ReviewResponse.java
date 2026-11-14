package com.rideshare.review.dto;

import com.rideshare.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID rideId,
        UserSummary reviewer,
        UUID reviewedUserId,
        int rating,
        String comment,
        Instant createdAt) {
}
