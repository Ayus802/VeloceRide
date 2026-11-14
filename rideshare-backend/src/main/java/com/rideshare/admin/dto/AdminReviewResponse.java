package com.rideshare.admin.dto;

import com.rideshare.review.entity.Review;
import com.rideshare.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record AdminReviewResponse(UUID id, UUID rideId, UserSummary reviewer, UserSummary reviewedUser, int rating,
                                  String comment, boolean hidden, String moderationNote, Instant createdAt) {

    public static AdminReviewResponse from(Review r) {
        return new AdminReviewResponse(r.getId(), r.getRide().getId(), UserSummary.from(r.getReviewer()),
                UserSummary.from(r.getReviewedUser()), r.getRating(), r.getComment(), r.isHidden(),
                r.getModerationNote(), r.getCreatedAt());
    }
}
