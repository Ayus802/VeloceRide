package com.rideshare.verification.dto;

import com.rideshare.common.enums.VerificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** Request/response records for email & phone verification. */
public final class VerificationDtos {

    private VerificationDtos() {
    }

    @Schema(description = "Verification state of one channel")
    public record ChannelStatus(
            VerificationChannel channel,
            @Schema(description = "Masked destination", example = "a***@example.com") String destination,
            boolean verified,
            @Schema(description = "A code is currently valid for this channel") boolean codeSent,
            @Schema(description = "Seconds until a new code may be requested", example = "42") long resendAvailableInSeconds) {
    }

    @Schema(description = "What the client must verify before tokens are issued")
    public record VerificationChallenge(
            @Schema(description = "Short-lived token to send with confirm/resend calls") String verificationToken,
            @Schema(description = "Token lifetime in seconds", example = "1800") long expiresIn,
            List<ChannelStatus> channels) {
    }

    public record ConfirmCodeRequest(
            @NotBlank String verificationToken,
            @NotNull VerificationChannel channel,
            @Schema(example = "482913") @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be the 6-digit code") String code) {
        @Override
        public String toString() {
            return "ConfirmCodeRequest[channel=" + channel + "]";
        }
    }

    public record ResendCodeRequest(@NotBlank String verificationToken, @NotNull VerificationChannel channel) {
        @Override
        public String toString() {
            return "ResendCodeRequest[channel=" + channel + "]";
        }
    }

    /** For signed-in users re-verifying (e.g. after changing their phone number). */
    public record CodeOnlyRequest(
            @Schema(example = "482913") @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be the 6-digit code") String code) {
        @Override
        public String toString() {
            return "CodeOnlyRequest[***]";
        }
    }
}
