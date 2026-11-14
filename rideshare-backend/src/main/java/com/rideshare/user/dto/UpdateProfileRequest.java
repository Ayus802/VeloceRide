package com.rideshare.user.dto;

import com.rideshare.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Profile update. Email is the login identity and cannot be changed here.")
public record UpdateProfileRequest(
        @Schema(example = "Asha Verma") @NotBlank @Size(min = 2, max = 100) String fullName,
        @Schema(example = "+919876543210") @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phoneNumber,
        @Schema(example = "https://cdn.example.com/u/asha.jpg", description = "Optional absolute http(s) URL")
        @Size(max = 1000) @URL(regexp = "^https?://.*", message = "must be a valid http(s) URL") String profilePicture) {
}
