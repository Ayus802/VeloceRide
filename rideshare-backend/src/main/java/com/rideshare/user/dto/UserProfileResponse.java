package com.rideshare.user.dto;

import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.enums.ActiveMode;
import com.rideshare.common.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "The authenticated user's own profile. The same profile is used in RIDER and PASSENGER mode.")
public record UserProfileResponse(
        UUID id,
        @Schema(example = "Asha Verma") String fullName,
        @Schema(example = "asha@example.com") String email,
        @Schema(example = "+919876543210") String phoneNumber,
        @Schema(example = "https://cdn.example.com/u/asha.jpg") String profilePicture,
        @Schema(example = "PASSENGER") ActiveMode activeMode,
        AccountStatus accountStatus,
        Role role,
        @Schema(example = "4.7") Double averageRating,
        @Schema(example = "12") long totalReviews,
        boolean emailVerified,
        boolean phoneVerified,
        @Schema(description = "Account can sign in with Google") boolean googleLinked,
        @Schema(description = "Account has a password (false for Google-only accounts)") boolean passwordSet,
        Instant createdAt,
        Instant updatedAt) {
}
