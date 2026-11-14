package com.rideshare.security;

import com.rideshare.common.enums.Role;
import com.rideshare.config.JwtConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdefghijklmnopqrstuvwxyz";
    private final JwtConfig config = new JwtConfig(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "rideshare-backend");
    private final UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "a@b.com", "x", Role.USER, true);

    @Test
    void issuedTokenValidatesToUserId() {
        JwtTokenProvider provider = new JwtTokenProvider(config, Clock.systemUTC());
        String token = provider.generateAccessToken(principal);
        assertThat(provider.validateAccessToken(token)).contains(principal.id());
        assertThat(provider.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void expiredTokenIsRejected() {
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        String token = new JwtTokenProvider(config, Clock.fixed(issued, ZoneOffset.UTC)).generateAccessToken(principal);
        JwtTokenProvider later = new JwtTokenProvider(config, Clock.fixed(issued.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        assertThat(later.validateAccessToken(token)).isEmpty();
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(config, Clock.systemUTC());
        String token = provider.generateAccessToken(principal);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThat(provider.validateAccessToken(tampered)).isEmpty();
        JwtTokenProvider other = new JwtTokenProvider(
                new JwtConfig(SECRET + "-other", Duration.ofMinutes(15), Duration.ofDays(7), "rideshare-backend"),
                Clock.systemUTC());
        assertThat(other.validateAccessToken(token)).isEmpty();
        assertThat(provider.validateAccessToken("garbage")).isEmpty();
    }

    @Test
    void weakOrMissingSecretFailsFast() {
        assertThatThrownBy(() -> new JwtConfig("short", null, null, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtConfig("${JWT_SECRET}", null, null, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtConfig(null, null, null, null)).isInstanceOf(IllegalStateException.class);
    }
}
