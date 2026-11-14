package com.rideshare.security;

import com.rideshare.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates short-lived HS256 access tokens. Refresh tokens are opaque random values
 * handled by the auth module, not JWTs.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_VERIFY = "verify";
    private static final String TYPE_GOOGLE_SIGNUP = "google_signup";

    private final JwtConfig config;
    private final SecretKey key;
    private final Clock clock;

    public JwtTokenProvider(JwtConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.id().toString())
                .issuer(config.issuer())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLE, principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(config.accessExpiration())))
                .signWith(key)
                .compact();
    }

    /** @return the user id if the token is a valid, unexpired access token issued by this server */
    public Optional<UUID> validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(config.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long accessTokenTtlSeconds() {
        return config.accessExpiration().toSeconds();
    }

    // ------------------------------------------------------------------ verification / Google sign-up tokens

    /** Proves the caller just passed the password or Google check for {@code userId}; used only to enter codes. */
    public String generateVerificationToken(UUID userId, Duration ttl) {
        return shortLived(TYPE_VERIFY, userId.toString(), ttl).compact();
    }

    public Optional<UUID> validateVerificationToken(String token) {
        return parse(token, TYPE_VERIFY).map(c -> UUID.fromString(c.getSubject()));
    }

    /** Carries a verified Google identity while a new user adds their phone number. */
    public String generateGoogleSignupToken(GoogleSignupClaims claims, Duration ttl) {
        return shortLived(TYPE_GOOGLE_SIGNUP, claims.googleSubject(), ttl)
                .claim("email", claims.email())
                .claim("name", claims.fullName())
                .claim("picture", claims.picture())
                .compact();
    }

    public Optional<GoogleSignupClaims> validateGoogleSignupToken(String token) {
        return parse(token, TYPE_GOOGLE_SIGNUP).map(c -> new GoogleSignupClaims(c.getSubject(),
                c.get("email", String.class), c.get("name", String.class), c.get("picture", String.class)));
    }

    public record GoogleSignupClaims(String googleSubject, String email, String fullName, String picture) {
    }

    private io.jsonwebtoken.JwtBuilder shortLived(String type, String subject, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuer(config.issuer())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key);
    }

    private Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(config.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return expectedType.equals(claims.get(CLAIM_TYPE, String.class)) ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected {} token: {}", expectedType, e.getMessage());
            return Optional.empty();
        }
    }
}
