package com.rideshare.auth.controller;

import com.rideshare.auth.dto.AuthFlowDtos.AuthFlowResponse;
import com.rideshare.auth.dto.AuthFlowDtos.GoogleSignInRequest;
import com.rideshare.auth.dto.AuthFlowDtos.GoogleSignupCompleteRequest;
import com.rideshare.auth.dto.AuthResponse;
import com.rideshare.verification.dto.VerificationDtos.ConfirmCodeRequest;
import com.rideshare.verification.dto.VerificationDtos.ResendCodeRequest;
import com.rideshare.auth.dto.ForgotPasswordRequest;
import com.rideshare.auth.dto.LoginRequest;
import com.rideshare.auth.dto.LogoutRequest;
import com.rideshare.auth.dto.RefreshTokenRequest;
import com.rideshare.auth.dto.RegisterRequest;
import com.rideshare.auth.dto.ResetPasswordRequest;
import com.rideshare.auth.service.AuthService;
import com.rideshare.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements // public endpoints
@Tag(name = "1. Authentication", description = "Registration, login, token refresh, logout and password reset")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates ONE account usable for both publishing rides (RIDER) and booking rides (PASSENGER). "
                    + "Sends a 6-digit code to the email address and one by SMS; returns status VERIFICATION_REQUIRED "
                    + "with a verificationToken. Tokens are issued once both codes are confirmed. "
                    + "409 if email/phone already exists; 503 if codes cannot be delivered.")
    public ResponseEntity<ApiResponse<AuthFlowResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration started. Enter the codes we sent you.", authService.register(request)));
    }

    @PostMapping("/verification/confirm")
    @Operation(summary = "Confirm an email or SMS code",
            description = "Returns AUTHENTICATED with tokens once both channels are verified, otherwise "
                    + "VERIFICATION_REQUIRED with the remaining channel. 400 wrong/expired code (attempts are limited); "
                    + "401 expired verification token.")
    public ResponseEntity<ApiResponse<AuthFlowResponse>> confirmCode(@Valid @RequestBody ConfirmCodeRequest request) {
        AuthFlowResponse result = authService.confirmCode(request);
        return ResponseEntity.ok(ApiResponse.success(
                result.auth() != null ? "Verification complete" : "Code confirmed", result));
    }

    @PostMapping("/verification/resend")
    @Operation(summary = "Send a new code", description = "429 during the resend cooldown or after the hourly limit.")
    public ResponseEntity<ApiResponse<AuthFlowResponse>> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("A new code has been sent", authService.resendCode(request)));
    }

    @PostMapping("/google")
    @Operation(summary = "Sign in or sign up with Google",
            description = "Send the ID token from Google Identity Services. Returns AUTHENTICATED, "
                    + "VERIFICATION_REQUIRED (phone still unverified) or PHONE_REQUIRED (new user must add a phone "
                    + "number via /google/complete). Existing accounts with the same verified email are linked. "
                    + "401 invalid token; 503 when Google sign-in is not configured.")
    public ResponseEntity<ApiResponse<AuthFlowResponse>> google(@Valid @RequestBody GoogleSignInRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Google sign-in", authService.google(request)));
    }

    @PostMapping("/google/complete")
    @Operation(summary = "Finish a Google sign-up by adding a phone number",
            description = "Creates the account (email verified by Google) and sends an SMS code. "
                    + "Returns VERIFICATION_REQUIRED for the phone number.")
    public ResponseEntity<ApiResponse<AuthFlowResponse>> completeGoogle(
            @Valid @RequestBody GoogleSignupCompleteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created. Verify your phone number.", authService.completeGoogleSignup(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password", description = "401 for wrong credentials, 403 if "
            + "deactivated, 403 with data.status=VERIFICATION_REQUIRED (and fresh codes sent) if the email or "
            + "phone number is not verified yet.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for new tokens",
            description = "Refresh tokens are single use and rotated. Re-using a revoked token revokes all sessions.")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", authService.refresh(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revokes the refresh token (optionally all sessions). Idempotent.")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link",
            description = "Always returns 200 to avoid revealing which emails are registered.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("If the email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset the password using a reset token", description = "Signs out all existing sessions.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset"));
    }
}
