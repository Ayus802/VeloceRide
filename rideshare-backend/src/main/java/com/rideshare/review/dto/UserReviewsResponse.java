package com.rideshare.review.dto;

import com.rideshare.common.response.PageResponse;

public record UserReviewsResponse(UserRatingResponse rating, PageResponse<ReviewResponse> reviews) {
}
