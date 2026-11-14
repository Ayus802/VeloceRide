package com.rideshare.auth.google;

import com.rideshare.common.exception.ServiceUnavailableException;
import com.rideshare.common.exception.UnauthorizedException;
import com.rideshare.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * Validates Google ID tokens locally: RS256 signature against Google's published keys (JWKS, cached by
 * Nimbus), issuer, audience = our OAuth client id, and expiry. No Google client secret is needed because
 * the browser obtains the ID token directly from Google Identity Services.
 */
@Slf4j
@Component
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final AppProperties properties;
    private volatile JwtDecoder decoder;

    public NimbusGoogleIdTokenVerifier(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (!properties.google().isConfigured()) {
            throw new ServiceUnavailableException("Google sign-in is not available right now");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            log.debug("Rejected Google ID token: {}", e.getMessage());
            throw new UnauthorizedException("Google sign-in failed. Please try again.");
        }
        Object verified = jwt.getClaim("email_verified");
        return new GoogleIdentity(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified)),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"));
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                if (decoder == null) {
                    String clientId = properties.google().clientId().trim();
                    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
                    nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                                    iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString())),
                            new JwtClaimValidator<Object>(JwtClaimNames.AUD, aud -> audienceContains(aud, clientId))));
                    decoder = nimbus;
                }
                current = decoder;
            }
        }
        return current;
    }

    private static boolean audienceContains(Object aud, String clientId) {
        if (aud instanceof Collection<?> values) {
            return values.stream().anyMatch(v -> clientId.equals(String.valueOf(v)));
        }
        return aud != null && clientId.equals(aud.toString());
    }
}
