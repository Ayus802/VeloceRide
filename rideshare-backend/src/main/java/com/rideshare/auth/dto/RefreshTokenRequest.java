package com.rideshare.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @Schema(description = "Refresh token returned by login/refresh") @NotBlank String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[***]";
    }
}
