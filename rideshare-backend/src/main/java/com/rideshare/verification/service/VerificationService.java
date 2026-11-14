package com.rideshare.verification.service;

import com.rideshare.common.enums.VerificationChannel;
import com.rideshare.common.exception.BadRequestException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.exception.ServiceUnavailableException;
import com.rideshare.common.exception.TooManyRequestsException;
import com.rideshare.common.exception.UnauthorizedException;
import com.rideshare.common.util.AfterCommit;
import com.rideshare.common.util.TokenUtils;
import com.rideshare.config.AppProperties;
import com.rideshare.security.JwtTokenProvider;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import com.rideshare.verification.delivery.OtpDispatcher;
import com.rideshare.verification.delivery.ProviderOtpDispatcher;
import com.rideshare.verification.dto.VerificationDtos.ChannelStatus;
import com.rideshare.verification.dto.VerificationDtos.VerificationChallenge;
import com.rideshare.verification.entity.VerificationCode;
import com.rideshare.verification.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Email and phone verification with 6-digit one-time codes.
 * <ul>
 *   <li>Codes are random (SecureRandom), stored as SHA-256 hashes bound to user, channel and destination.</li>
 *   <li>A code expires after {@code app.otp.validity}, allows {@code max-attempts} wrong guesses, and is single use.</li>
 *   <li>New codes are limited by a resend cooldown and an hourly cap per channel.</li>
 *   <li>Codes are delivered only after the transaction commits.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final OtpDispatcher dispatcher;
    private final JwtTokenProvider tokenProvider;
    private final AppProperties properties;
    private final Clock clock;

    /** Throws 503 when codes for a channel the user still needs cannot be delivered (no provider configured). */
    public void requireDeliverable(VerificationChannel... channels) {
        for (VerificationChannel channel : channels) {
            if (!dispatcher.canDeliver(channel)) {
                throw new ServiceUnavailableException(channel == VerificationChannel.EMAIL
                        ? "Email verification is temporarily unavailable. Please try again later."
                        : "Phone verification is temporarily unavailable. Please try again later.");
            }
        }
    }

    /**
     * Builds the challenge for a user who still has unverified channels. When {@code sendCodes} is true a code is
     * sent for every pending channel that has no recently sent, still valid code.
     */
    @Transactional
    public VerificationChallenge challenge(User user, boolean sendCodes) {
        if (sendCodes) {
            for (VerificationChannel channel : pending(user)) {
                requireDeliverable(channel);
                try {
                    sendIfNeeded(user, channel);
                } catch (TooManyRequestsException e) {
                    // Hourly cap reached: still return the challenge; an earlier code may be valid, and
                    // the client shows when a new one can be requested.
                    log.info("Code for {} not re-sent for user {}: {}", channel, user.getId(), e.getMessage());
                }
            }
        }
        Duration ttl = properties.otp().challengeValidity();
        return new VerificationChallenge(tokenProvider.generateVerificationToken(user.getId(), ttl), ttl.toSeconds(),
                statuses(user));
    }

    /** Resolves the user behind a verification token (401 when invalid or expired). */
    public User userForToken(String verificationToken) {
        UUID userId = tokenProvider.validateVerificationToken(verificationToken)
                .orElseThrow(() -> new UnauthorizedException("This verification session has expired. Please sign in again."));
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("This verification session has expired. Please sign in again."));
    }

    /** Sends a new code now, enforcing the cooldown and hourly cap (429). */
    @Transactional
    public ChannelStatus resend(User user, VerificationChannel channel) {
        if (isVerified(user, channel)) {
            throw new BadRequestException(label(channel) + " is already verified");
        }
        requireDeliverable(channel);
        Instant now = clock.instant();
        // Cooldown applies per destination: a code sent to a previous phone number doesn't block the new one.
        latest(user, channel).filter(last -> last.getDestination().equals(destination(user, channel))).ifPresent(last -> {
            Duration wait = Duration.between(now, last.getCreatedAt().plus(properties.otp().resendCooldown()));
            if (!wait.isNegative() && !wait.isZero()) {
                throw new TooManyRequestsException("Please wait " + Math.max(1, wait.toSeconds())
                        + " seconds before requesting a new code");
            }
        });
        issue(user, channel, now);
        return status(user, channel);
    }

    /**
     * Checks a code. Wrong guesses are counted (and committed even though an error is returned).
     * On success the channel is marked verified on the user.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void confirm(User user, VerificationChannel channel, String code) {
        if (isVerified(user, channel)) {
            return; // idempotent
        }
        Instant now = clock.instant();
        int maxAttempts = properties.otp().maxAttempts();
        VerificationCode current = codeRepository.findOpenForUpdate(user.getId(), channel).stream().findFirst()
                .filter(c -> c.getDestination().equals(destination(user, channel)))
                .orElseThrow(() -> new BadRequestException("No active code. Request a new one."));
        if (!current.isUsable(now, maxAttempts)) {
            current.setConsumedAt(now);
            throw new BadRequestException(current.getExpiresAt().isAfter(now)
                    ? "Too many incorrect attempts. Request a new code."
                    : "This code has expired. Request a new one.");
        }
        if (!MessageDigest.isEqual(current.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(user.getId(), channel, current.getDestination(), code.trim()).getBytes(StandardCharsets.US_ASCII))) {
            current.setAttempts(current.getAttempts() + 1);
            int left = maxAttempts - current.getAttempts();
            if (left <= 0) {
                current.setConsumedAt(now);
                throw new BadRequestException("Too many incorrect attempts. Request a new code.");
            }
            throw new BadRequestException("Incorrect code. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
        }
        current.setConsumedAt(now);
        if (channel == VerificationChannel.EMAIL) {
            user.setEmailVerified(true);
        } else {
            user.setPhoneVerified(true);
        }
        userRepository.save(user);
    }

    public List<VerificationChannel> pending(User user) {
        List<VerificationChannel> pending = new ArrayList<>(2);
        if (!user.isEmailVerified()) {
            pending.add(VerificationChannel.EMAIL);
        }
        if (!user.isPhoneVerified()) {
            pending.add(VerificationChannel.PHONE);
        }
        return pending;
    }

    public List<ChannelStatus> statuses(User user) {
        return List.of(status(user, VerificationChannel.EMAIL), status(user, VerificationChannel.PHONE));
    }

    public ChannelStatus status(User user, VerificationChannel channel) {
        Instant now = clock.instant();
        var last = latest(user, channel);
        boolean codeSent = last.filter(c -> c.isUsable(now, properties.otp().maxAttempts())
                && c.getDestination().equals(destination(user, channel))).isPresent();
        long wait = last.filter(c -> c.getDestination().equals(destination(user, channel)))
                .map(c -> Duration.between(now, c.getCreatedAt().plus(properties.otp().resendCooldown())).toSeconds())
                .orElse(0L);
        return new ChannelStatus(channel, ProviderOtpDispatcher.mask(destination(user, channel)), isVerified(user, channel),
                codeSent, Math.max(0, wait));
    }

    // ------------------------------------------------------------------ internals

    private void sendIfNeeded(User user, VerificationChannel channel) {
        Instant now = clock.instant();
        // Don't spam: a still-valid code for the current destination is kept (the user can press "Resend").
        boolean validCodeExists = latest(user, channel)
                .filter(c -> c.isUsable(now, properties.otp().maxAttempts()))
                .filter(c -> c.getDestination().equals(destination(user, channel)))
                .isPresent();
        if (!validCodeExists) {
            issue(user, channel, now);
        }
    }

    private void issue(User user, VerificationChannel channel, Instant now) {
        long sentLastHour = codeRepository.countByUserIdAndChannelAndCreatedAtAfter(user.getId(), channel,
                now.minus(Duration.ofHours(1)));
        if (sentLastHour >= properties.otp().maxSendsPerHour()) {
            throw new TooManyRequestsException("Too many codes requested. Please try again later.");
        }
        codeRepository.invalidateOpen(user.getId(), channel, now);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String destination = destination(user, channel);
        VerificationCode entity = new VerificationCode();
        entity.setUser(userRepository.getReferenceById(user.getId()));
        entity.setChannel(channel);
        entity.setDestination(destination);
        entity.setCodeHash(hash(user.getId(), channel, destination, code));
        entity.setExpiresAt(now.plus(properties.otp().validity()));
        codeRepository.saveAndFlush(entity);
        String name = user.getFullName();
        AfterCommit.run(() -> {
            try {
                dispatcher.send(channel, destination, name, code);
            } catch (RuntimeException e) {
                // The user can request a new code; never leak provider errors to the client.
                log.error("Failed to deliver {} verification code to {}: {}", channel,
                        ProviderOtpDispatcher.mask(destination), e.getMessage());
            }
        });
    }

    private java.util.Optional<VerificationCode> latest(User user, VerificationChannel channel) {
        return codeRepository.findLatest(user.getId(), channel).stream().findFirst();
    }

    private static boolean isVerified(User user, VerificationChannel channel) {
        return channel == VerificationChannel.EMAIL ? user.isEmailVerified() : user.isPhoneVerified();
    }

    private static String destination(User user, VerificationChannel channel) {
        return channel == VerificationChannel.EMAIL ? user.getEmail() : user.getPhoneNumber();
    }

    private static String label(VerificationChannel channel) {
        return channel == VerificationChannel.EMAIL ? "Email address" : "Phone number";
    }

    private static String hash(UUID userId, VerificationChannel channel, String destination, String code) {
        return TokenUtils.sha256Hex(userId + ":" + channel + ":" + destination + ":" + code);
    }

    // ------------------------------------------------------------------ signed-in user (e.g. after a phone change)

    @Transactional
    public ChannelStatus sendForUser(UUID userId, VerificationChannel channel) {
        return resend(load(userId), channel);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public User confirmForUser(UUID userId, VerificationChannel channel, String code) {
        User user = load(userId);
        confirm(user, channel, code);
        return user;
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User"));
    }
}
