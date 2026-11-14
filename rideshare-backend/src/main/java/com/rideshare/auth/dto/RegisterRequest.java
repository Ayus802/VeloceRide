package com.rideshare.auth.dto;

import com.rideshare.common.validation.PasswordsMatch;
import com.rideshare.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordsMatch
@Schema(description = "Single registration for both driving and booking")
public record RegisterRequest(
        @Schema(example = "Asha Verma") @NotBlank @Size(min = 2, max = 100) String fullName,
        @Schema(example = "asha@example.com") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "+919876543210") @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phoneNumber,
        @Schema(example = "Secret@123", description = "8-64 chars with upper, lower, digit and special character")
        @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String password,
        @Schema(example = "Secret@123") @NotBlank String confirmPassword) implements PasswordsMatch.Confirmable {

    @Override
    public String passwordToConfirm() {
        return password;
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
