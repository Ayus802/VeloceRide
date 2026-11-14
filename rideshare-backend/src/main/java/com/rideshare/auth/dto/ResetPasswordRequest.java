package com.rideshare.auth.dto;

import com.rideshare.common.validation.PasswordsMatch;
import com.rideshare.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@PasswordsMatch
public record ResetPasswordRequest(
        @Schema(description = "Token delivered out-of-band (email link)") @NotBlank String token,
        @Schema(example = "NewPass@456") @NotBlank
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String newPassword,
        @Schema(example = "NewPass@456") @NotBlank String confirmPassword) implements PasswordsMatch.Confirmable {

    @Override
    public String passwordToConfirm() {
        return newPassword;
    }

    @Override
    public String toString() {
        return "ResetPasswordRequest[***]";
    }
}
