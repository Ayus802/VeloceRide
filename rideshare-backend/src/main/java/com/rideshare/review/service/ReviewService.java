package com.rideshare.review.service;

import com.rideshare.review.dto.CreateReviewRequest;
import com.rideshare.review.dto.ReviewResponse;
import com.rideshare.review.dto.UserRatingResponse;
import com.rideshare.review.dto.UserReviewsResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    ReviewResponse create(UUID reviewerId, CreateReviewRequest request);

    UserReviewsResponse getUserReviews(UUID userId, Pageable pageable);

    UserRatingResponse getUserRating(UUID userId);

    List<ReviewResponse> getRideReviews(UUID rideId);
}
