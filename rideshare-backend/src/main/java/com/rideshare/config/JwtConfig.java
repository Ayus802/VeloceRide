package com.rideshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings bound from {@code app.jwt.*}. Startup fails fast if the secret is missing or weak.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtConfig(String secret, Duration accessExpiration, Duration refreshExpiration, String issuer) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtConfig {
        if (secret == null || secret.isBlank() || secret.contains("${")) {
            throw new IllegalStateException("JWT_SECRET must be configured (app.jwt.secret)");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (accessExpiration == null || accessExpiration.isNegative() || accessExpiration.isZero()) {
            accessExpiration = Duration.ofMinutes(15);
        }
        if (refreshExpiration == null || refreshExpiration.isNegative() || refreshExpiration.isZero()) {
            refreshExpiration = Duration.ofDays(7);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "rideshare-backend";
        }
    }
}
