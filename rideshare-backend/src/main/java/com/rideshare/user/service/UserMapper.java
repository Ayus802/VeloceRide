package com.rideshare.user.service;

import com.rideshare.review.dto.UserRatingResponse;
import com.rideshare.review.service.RatingService;
import com.rideshare.user.dto.PublicUserResponse;
import com.rideshare.user.dto.UserProfileResponse;
import com.rideshare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final RatingService ratingService;

    public UserProfileResponse toProfile(User user) {
        UserRatingResponse rating = ratingService.getRating(user.getId());
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
                user.getProfilePicture(), user.getActiveMode(), user.getAccountStatus(), user.getRole(),
                rating.averageRating(), rating.totalReviews(), user.isEmailVerified(), user.isPhoneVerified(),
                user.getGoogleSubject() != null, user.getPassword() != null, user.getCreatedAt(), user.getUpdatedAt());
    }

    public PublicUserResponse toPublic(User user) {
        UserRatingResponse rating = ratingService.getRating(user.getId());
        return new PublicUserResponse(user.getId(), user.getFullName(), user.getProfilePicture(),
                rating.averageRating(), rating.totalReviews(), user.getCreatedAt());
    }
}
