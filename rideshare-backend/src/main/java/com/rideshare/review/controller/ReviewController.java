package com.rideshare.review.controller;

import com.rideshare.common.response.ApiResponse;
import com.rideshare.review.dto.CreateReviewRequest;
import com.rideshare.review.dto.ReviewResponse;
import com.rideshare.review.dto.UserRatingResponse;
import com.rideshare.review.dto.UserReviewsResponse;
import com.rideshare.review.service.ReviewService;
import com.rideshare.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "7. Reviews & Ratings", description = "Participants of a completed ride rate each other (1-5)")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/reviews")
    @Operation(summary = "Submit a review",
            description = "403 if you were not a participant; 422 if the ride is not completed, for self-reviews, "
                    + "or passenger-to-passenger reviews; 409 for duplicates; 400 if rating is outside 1-5.")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted", reviewService.create(principal.id(), request)));
    }

    @GetMapping("/users/{userId}/reviews")
    @Operation(summary = "Reviews received by a user, with their average rating",
            description = "Sortable by createdAt (default desc) or rating.")
    public ResponseEntity<ApiResponse<UserReviewsResponse>> userReviews(@PathVariable UUID userId,
                                                                        @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved", reviewService.getUserReviews(userId, pageable)));
    }

    @GetMapping("/users/{userId}/rating")
    @Operation(summary = "Average rating and total review count of a user")
    public ResponseEntity<ApiResponse<UserRatingResponse>> userRating(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Rating retrieved", reviewService.getUserRating(userId)));
    }

    @GetMapping("/rides/{rideId}/reviews")
    @Operation(summary = "Reviews written for a ride")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> rideReviews(@PathVariable UUID rideId) {
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved", reviewService.getRideReviews(rideId)));
    }
}
