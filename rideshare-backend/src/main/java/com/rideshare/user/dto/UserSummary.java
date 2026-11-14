package com.rideshare.user.dto;

import com.rideshare.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Minimal user reference embedded in other resources")
public record UserSummary(UUID id, String fullName, String profilePicture) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getProfilePicture());
    }
}
