package com.rideshare.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Public view of another user. Contact details are never included.")
public record PublicUserResponse(
        UUID id,
        String fullName,
        String profilePicture,
        Double averageRating,
        long totalReviews,
        Instant memberSince) {
}
