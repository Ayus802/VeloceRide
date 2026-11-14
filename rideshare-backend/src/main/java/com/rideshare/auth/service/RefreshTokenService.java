package com.rideshare.auth.service;

import com.rideshare.auth.entity.RefreshToken;
import com.rideshare.auth.repository.RefreshTokenRepository;
import com.rideshare.common.exception.UnauthorizedException;
import com.rideshare.common.util.TokenUtils;
import com.rideshare.config.JwtConfig;
import com.rideshare.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection:
 * every refresh revokes the presented token and issues a new one; presenting an already revoked
 * token is treated as theft and revokes all of the user's sessions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository repository;
    private final JwtConfig jwtConfig;
    private final Clock clock;

    /** Result of a successful rotation. */
    public record Rotation(User user, String newRawToken) {
    }

    @Transactional
    public String issue(User user) {
        return persistNew(user).raw();
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHashForUpdate(TokenUtils.sha256Hex(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        Instant now = clock.instant();
        if (current.isRevoked()) {
            log.warn("Refresh token reuse detected for user {}; revoking all sessions", current.getUser().getId());
            repository.revokeAllForUser(current.getUser().getId(), now);
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (current.isExpired(now)) {
            throw new UnauthorizedException("Refresh token has expired");
        }
        User user = current.getUser();
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }
        Issued next = persistNew(user);
        current.setRevokedAt(now);
        current.setReplacedBy(next.id());
        return new Rotation(user, next.raw());
    }

    /** Revokes a single token. Unknown tokens are ignored so logout is idempotent. */
    @Transactional
    public void revoke(String rawToken, boolean allDevices) {
        repository.findByTokenHash(TokenUtils.sha256Hex(rawToken)).ifPresent(token -> {
            if (allDevices) {
                repository.revokeAllForUser(token.getUser().getId(), clock.instant());
            } else if (!token.isRevoked()) {
                token.setRevokedAt(clock.instant());
            }
        });
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private record Issued(UUID id, String raw) {
    }

    private Issued persistNew(User user) {
        String raw = TokenUtils.randomToken(TOKEN_BYTES);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenUtils.sha256Hex(raw));
        token.setExpiresAt(clock.instant().plus(jwtConfig.refreshExpiration()));
        repository.save(token);
        return new Issued(token.getId(), raw);
    }
}
