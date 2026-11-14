package com.rideshare.auth.service;

import com.rideshare.auth.dto.AuthFlowDtos.AuthFlowResponse;
import com.rideshare.auth.dto.AuthFlowDtos.GoogleSignInRequest;
import com.rideshare.auth.dto.AuthFlowDtos.GoogleSignupCompleteRequest;
import com.rideshare.auth.dto.AuthFlowDtos.GoogleSignupResponse;
import com.rideshare.auth.dto.AuthResponse;
import com.rideshare.auth.google.GoogleIdTokenVerifier;
import com.rideshare.auth.google.GoogleIdTokenVerifier.GoogleIdentity;
import com.rideshare.common.enums.VerificationChannel;
import com.rideshare.common.exception.VerificationRequiredException;
import com.rideshare.verification.dto.VerificationDtos.ConfirmCodeRequest;
import com.rideshare.verification.dto.VerificationDtos.ResendCodeRequest;
import com.rideshare.verification.service.VerificationService;
import org.springframework.security.authentication.DisabledException;
import java.time.Duration;
import com.rideshare.auth.dto.ForgotPasswordRequest;
import com.rideshare.auth.dto.LoginRequest;
import com.rideshare.auth.dto.LogoutRequest;
import com.rideshare.auth.dto.RefreshTokenRequest;
import com.rideshare.auth.dto.RegisterRequest;
import com.rideshare.auth.dto.ResetPasswordRequest;
import com.rideshare.auth.entity.PasswordResetToken;
import com.rideshare.auth.repository.PasswordResetTokenRepository;
import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.enums.ActiveMode;
import com.rideshare.common.enums.Role;
import com.rideshare.common.exception.BadRequestException;
import com.rideshare.common.exception.DuplicateResourceException;
import com.rideshare.common.exception.UnauthorizedException;
import com.rideshare.common.util.TokenUtils;
import com.rideshare.config.AppProperties;
import com.rideshare.security.JwtTokenProvider;
import com.rideshare.security.UserPrincipal;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import com.rideshare.user.service.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int RESET_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordResetNotifier resetNotifier;
    private final UserMapper userMapper;
    private final AppProperties properties;
    private final Clock clock;
    private final VerificationService verificationService;
    private final GoogleIdTokenVerifier googleVerifier;

    private static final Duration GOOGLE_SIGNUP_TTL = Duration.ofMinutes(15);

    @Override
    @Transactional
    public AuthFlowResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String phone = request.phoneNumber().trim();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new DuplicateResourceException("Phone number is already registered");
        }
        // Fail before creating the account if codes cannot be delivered.
        verificationService.requireDeliverable(VerificationChannel.EMAIL, VerificationChannel.PHONE);
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setActiveMode(ActiveMode.PASSENGER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setRole(Role.USER);
        userRepository.saveAndFlush(user);
        return AuthFlowResponse.verificationRequired(verificationService.challenge(user, true));
    }

    @Override
    // The challenge (and the codes it sends) must commit even though a 403 is returned.
    @Transactional(noRollbackFor = VerificationRequiredException.class)
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException (401) or DisabledException (403); handled globally.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.id()).orElseThrow();
        if (!user.isFullyVerified()) {
            throw new VerificationRequiredException(
                    AuthFlowResponse.verificationRequired(verificationService.challenge(user, true)));
        }
        return issueTokens(user);
    }

    // ------------------------------------------------------------------ verification

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthFlowResponse confirmCode(ConfirmCodeRequest request) {
        User user = verificationService.userForToken(request.verificationToken());
        requireActive(user);
        verificationService.confirm(user, request.channel(), request.code());
        if (user.isFullyVerified()) {
            return AuthFlowResponse.authenticated(issueTokens(user));
        }
        return AuthFlowResponse.verificationRequired(verificationService.challenge(user, false));
    }

    @Override
    @Transactional
    public AuthFlowResponse resendCode(ResendCodeRequest request) {
        User user = verificationService.userForToken(request.verificationToken());
        requireActive(user);
        verificationService.resend(user, request.channel());
        return AuthFlowResponse.verificationRequired(verificationService.challenge(user, false));
    }

    // ------------------------------------------------------------------ Google sign-in

    @Override
    @Transactional
    public AuthFlowResponse google(GoogleSignInRequest request) {
        GoogleIdentity identity = googleVerifier.verify(request.idToken());
        if (identity.email() == null || !identity.emailVerified()) {
            throw new BadRequestException("Your Google account email is not verified");
        }
        String email = normalizeEmail(identity.email());
        User user = userRepository.findByGoogleSubject(identity.subject())
                .or(() -> userRepository.findByEmail(email).map(existing -> linkGoogle(existing, identity)))
                .orElse(null);
        if (user == null) {
            // New user: Google verified the email; the platform also needs a verified phone number.
            JwtTokenProvider.GoogleSignupClaims claims = new JwtTokenProvider.GoogleSignupClaims(
                    identity.subject(), email, displayName(identity, email), safePicture(identity.picture()));
            return AuthFlowResponse.phoneRequired(new GoogleSignupResponse(
                    tokenProvider.generateGoogleSignupToken(claims, GOOGLE_SIGNUP_TTL), GOOGLE_SIGNUP_TTL.toSeconds(),
                    claims.email(), claims.fullName(), claims.picture()));
        }
        requireActive(user);
        if (!user.isFullyVerified()) {
            return AuthFlowResponse.verificationRequired(verificationService.challenge(user, true));
        }
        return AuthFlowResponse.authenticated(issueTokens(user));
    }

    @Override
    @Transactional
    public AuthFlowResponse completeGoogleSignup(GoogleSignupCompleteRequest request) {
        JwtTokenProvider.GoogleSignupClaims claims = tokenProvider.validateGoogleSignupToken(request.signupToken())
                .orElseThrow(() -> new UnauthorizedException("Your Google sign-up session has expired. Please try again."));
        String phone = request.phoneNumber().trim();
        if (userRepository.findByGoogleSubject(claims.googleSubject()).isPresent()
                || userRepository.existsByEmail(claims.email())) {
            throw new DuplicateResourceException("An account already exists for this Google account. Sign in with Google.");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new DuplicateResourceException("Phone number is already registered");
        }
        verificationService.requireDeliverable(VerificationChannel.PHONE);
        User user = new User();
        user.setFullName(claims.fullName());
        user.setEmail(claims.email());
        user.setPhoneNumber(phone);
        user.setPassword(null);
        user.setProfilePicture(claims.picture());
        user.setGoogleSubject(claims.googleSubject());
        user.setEmailVerified(true); // asserted by Google (email_verified)
        user.setActiveMode(ActiveMode.PASSENGER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setRole(Role.USER);
        userRepository.saveAndFlush(user);
        return AuthFlowResponse.verificationRequired(verificationService.challenge(user, true));
    }

    /** Links an existing account with the same, Google-verified email to Google sign-in. */
    private User linkGoogle(User user, GoogleIdentity identity) {
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(identity.subject())) {
            throw new DuplicateResourceException("This email is linked to a different Google account");
        }
        user.setGoogleSubject(identity.subject());
        user.setEmailVerified(true);
        if (user.getProfilePicture() == null) {
            user.setProfilePicture(safePicture(identity.picture()));
        }
        return userRepository.saveAndFlush(user);
    }

    private static void requireActive(User user) {
        if (!user.isActive()) {
            throw new DisabledException("Account is deactivated");
        }
    }

    private static String displayName(GoogleIdentity identity, String email) {
        String name = identity.name() == null ? "" : identity.name().trim();
        if (name.length() < 2) {
            name = email.substring(0, email.indexOf('@'));
        }
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static String safePicture(String url) {
        return url != null && url.startsWith("https://") && url.length() <= 1000 ? url : null;
    }

    @Override
    // Reuse detection revokes every session and then throws; that revocation must commit, not roll back.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        User user = rotation.user();
        return new AuthResponse(tokenProvider.generateAccessToken(UserPrincipal.from(user)), rotation.newRawToken(),
                "Bearer", tokenProvider.accessTokenTtlSeconds(), userMapper.toProfile(user));
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken(), request.allDevices());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always succeed silently so the endpoint cannot be used to discover registered emails.
        userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(User::isActive)
                .ifPresent(user -> {
                    Instant now = clock.instant();
                    resetTokenRepository.invalidateAllForUser(user.getId(), now);
                    String raw = TokenUtils.randomToken(RESET_TOKEN_BYTES);
                    PasswordResetToken token = new PasswordResetToken();
                    token.setUser(user);
                    token.setTokenHash(TokenUtils.sha256Hex(raw));
                    token.setExpiresAt(now.plus(properties.passwordReset().tokenValidity()));
                    resetTokenRepository.save(token);
                    resetNotifier.sendResetLink(user, properties.passwordReset().frontendResetUrl() + "?token=" + raw);
                });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256Hex(request.token()))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BadRequestException("Reset token is invalid or has expired"));
        User user = token.getUser();
        if (!user.isActive()) {
            throw new BadRequestException("Reset token is invalid or has expired");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        token.setUsedAt(now);
        resetTokenRepository.invalidateAllForUser(user.getId(), now);
        refreshTokenService.revokeAll(user.getId()); // sign out every session after a reset
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = tokenProvider.generateAccessToken(UserPrincipal.from(user));
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, "Bearer", tokenProvider.accessTokenTtlSeconds(),
                userMapper.toProfile(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
