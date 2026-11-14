package com.rideshare.common.event;

import java.util.UUID;

/** Published when a review is submitted. */
public record ReviewEvent(UUID reviewId, UUID rideId, UUID reviewedUserId, String reviewerName, int rating) {
}
