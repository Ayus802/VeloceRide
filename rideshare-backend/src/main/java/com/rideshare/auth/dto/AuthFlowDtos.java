package com.rideshare.auth.dto;

import com.rideshare.common.validation.ValidationPatterns;
import com.rideshare.verification.dto.VerificationDtos.VerificationChallenge;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Multi-step sign-up / sign-in results (registration, verification, Google). */
public final class AuthFlowDtos {

    private AuthFlowDtos() {
    }

    public enum AuthFlowStatus {
        /** Tokens issued; {@code auth} is set. */
        AUTHENTICATED,
        /** Email and/or phone must be confirmed with a code; {@code verification} is set. */
        VERIFICATION_REQUIRED,
        /** New Google user must add a phone number; {@code googleSignup} is set. */
        PHONE_REQUIRED
    }

    @Schema(description = "Result of a sign-up / sign-in step. Exactly one of auth, verification, googleSignup is set.")
    public record AuthFlowResponse(AuthFlowStatus status, AuthResponse auth, VerificationChallenge verification,
                                   GoogleSignupResponse googleSignup) {

        public static AuthFlowResponse authenticated(AuthResponse auth) {
            return new AuthFlowResponse(AuthFlowStatus.AUTHENTICATED, auth, null, null);
        }

        public static AuthFlowResponse verificationRequired(VerificationChallenge challenge) {
            return new AuthFlowResponse(AuthFlowStatus.VERIFICATION_REQUIRED, null, challenge, null);
        }

        public static AuthFlowResponse phoneRequired(GoogleSignupResponse signup) {
            return new AuthFlowResponse(AuthFlowStatus.PHONE_REQUIRED, null, null, signup);
        }
    }

    @Schema(description = "Verified Google profile awaiting a phone number")
    public record GoogleSignupResponse(
            @Schema(description = "Short-lived token for POST /api/auth/google/complete") String signupToken,
            long expiresIn,
            String email,
            String fullName,
            String profilePicture) {
    }

    public record GoogleSignInRequest(
            @Schema(description = "ID token ('credential') returned by Google Identity Services") @NotBlank String idToken) {
        @Override
        public String toString() {
            return "GoogleSignInRequest[***]";
        }
    }

    public record GoogleSignupCompleteRequest(
            @NotBlank String signupToken,
            @Schema(example = "+919876543210") @NotBlank
            @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phoneNumber) {
        @Override
        public String toString() {
            return "GoogleSignupCompleteRequest[***]";
        }
    }
}
