package com.rideshare.admin.service;

import com.rideshare.admin.dto.AdminReviewResponse;
import com.rideshare.admin.dto.AdminUserResponse;
import com.rideshare.admin.dto.ModerateReviewRequest;
import com.rideshare.admin.dto.PlatformStatsResponse;
import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Admin-only operations. Ride, booking and vehicle listings reuse the domain services. */
public interface AdminService {

    PageResponse<AdminUserResponse> listUsers(String query, Pageable pageable);

    AdminUserResponse getUser(UUID userId);

    AdminUserResponse updateAccountStatus(UUID adminId, UUID userId, AccountStatus status);

    PageResponse<AdminReviewResponse> listReviews(Boolean hidden, Pageable pageable);

    AdminReviewResponse moderateReview(UUID reviewId, ModerateReviewRequest request);

    PlatformStatsResponse stats();
}
