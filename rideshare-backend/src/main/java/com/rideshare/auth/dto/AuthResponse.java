package com.rideshare.auth.dto;

import com.rideshare.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tokens issued on registration, login and refresh")
public record AuthResponse(
        @Schema(description = "Short-lived JWT, send as 'Authorization: Bearer <token>'") String accessToken,
        @Schema(description = "Opaque, single-use refresh token (rotated on every refresh)") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
        UserProfileResponse user) {

    @Override
    public String toString() {
        return "AuthResponse[user=" + (user == null ? null : user.id()) + "]";
    }
}
