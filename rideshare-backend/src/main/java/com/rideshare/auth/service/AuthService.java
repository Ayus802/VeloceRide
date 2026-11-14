package com.rideshare.auth.service;

import com.rideshare.auth.dto.AuthFlowDtos.AuthFlowResponse;
import com.rideshare.auth.dto.AuthFlowDtos.GoogleSignInRequest;
import com.rideshare.auth.dto.AuthFlowDtos.GoogleSignupCompleteRequest;
import com.rideshare.auth.dto.AuthResponse;
import com.rideshare.auth.dto.ForgotPasswordRequest;
import com.rideshare.auth.dto.LoginRequest;
import com.rideshare.auth.dto.LogoutRequest;
import com.rideshare.auth.dto.RefreshTokenRequest;
import com.rideshare.auth.dto.RegisterRequest;
import com.rideshare.auth.dto.ResetPasswordRequest;
import com.rideshare.verification.dto.VerificationDtos.ConfirmCodeRequest;
import com.rideshare.verification.dto.VerificationDtos.ResendCodeRequest;

public interface AuthService {

    /** Creates an unverified account and sends email + SMS codes. No tokens until both are confirmed. */
    AuthFlowResponse register(RegisterRequest request);

    /** Tokens for verified accounts; VerificationRequiredException (403 + challenge) otherwise. */
    AuthResponse login(LoginRequest request);

    AuthFlowResponse confirmCode(ConfirmCodeRequest request);

    AuthFlowResponse resendCode(ResendCodeRequest request);

    AuthFlowResponse google(GoogleSignInRequest request);

    AuthFlowResponse completeGoogleSignup(GoogleSignupCompleteRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
