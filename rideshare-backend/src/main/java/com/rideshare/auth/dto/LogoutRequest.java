package com.rideshare.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @Schema(description = "Refresh token to revoke") @NotBlank String refreshToken,
        @Schema(description = "Also revoke every other session of this account", example = "false") boolean allDevices) {

    @Override
    public String toString() {
        return "LogoutRequest[allDevices=" + allDevices + "]";
    }
}
