package com.rideshare.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideshare.config.JwtConfig;
import com.rideshare.support.AbstractIntegrationTest;
import com.rideshare.support.TestSupportConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JwtConfig jwtConfig;

    @Autowired
    TestSupportConfig.CapturingPasswordResetNotifier resetNotifier;

    @Test
    void registrationRequiresVerificationThenReturnsTokensAndSingleUnifiedProfile() throws Exception {
        String email = uniqueEmail();
        doPost("/api/auth/register", null, registration(email, uniquePhone()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.data.auth").doesNotExist())
                .andExpect(jsonPath("$.data.verification.verificationToken", notNullValue()));

        TestUser user = registerUser();
        doGet("/api/users/me", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeMode").value("PASSENGER"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.phoneVerified").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        String email = uniqueEmail();
        doPost("/api/auth/register", null, registration(email, uniquePhone())).andExpect(status().isCreated());
        doPost("/api/auth/register", null, registration(email.toUpperCase(), uniquePhone()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void duplicatePhoneIsRejectedWith409() throws Exception {
        String phone = uniquePhone();
        doPost("/api/auth/register", null, registration(uniqueEmail(), phone)).andExpect(status().isCreated());
        doPost("/api/auth/register", null, registration(uniqueEmail(), phone))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phone number is already registered"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        Map<String, Object> body = registration("not-an-email", "12");
        body.put("password", "weak");
        body.put("confirmPassword", "different");
        doPost("/api/auth/register", null, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("phoneNumber")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("confirmPassword")));
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        TestUser user = registerUser();
        doPost("/api/auth/login", null, Map.of("email", user.email(), "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.id").value(user.id().toString()));
    }

    @Test
    void loginFailsWithIncorrectPassword() throws Exception {
        TestUser user = registerUser();
        doPost("/api/auth/login", null, Map.of("email", user.email(), "password", "Wrong@1234"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        doPost("/api/auth/login", null, Map.of("email", "nobody@test.com", "password", "Wrong@1234"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        TestUser user = registerUser();
        String expired = Jwts.builder()
                .subject(user.id().toString())
                .issuer(jwtConfig.issuer())
                .claim("typ", "access")
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(jwtConfig.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        TestUser user = registerUser();
        String forged = Jwts.builder()
                .subject(user.id().toString())
                .issuer(jwtConfig.issuer())
                .claim("typ", "access")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokensAndDetectsReuse() throws Exception {
        TestUser user = registerUser();
        JsonNode refreshed = data(doPost("/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isOk()));
        String newRefresh = refreshed.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(user.refreshToken());

        // Re-using the rotated token is rejected and revokes the whole family.
        doPost("/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isUnauthorized());
        doPost("/api/auth/refresh", null, Map.of("refreshToken", newRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        TestUser user = registerUser();
        doPost("/api/auth/logout", null, Map.of("refreshToken", user.refreshToken(), "allDevices", false))
                .andExpect(status().isOk());
        doPost("/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotAndResetPasswordFlow() throws Exception {
        TestUser user = registerUser();
        doPost("/api/auth/forgot-password", null, Map.of("email", user.email())).andExpect(status().isOk());
        // Unknown emails get the same response (no account enumeration).
        doPost("/api/auth/forgot-password", null, Map.of("email", "unknown@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If the email is registered")));

        String token = resetNotifier.tokenFor(user.email()).orElseThrow();
        doPost("/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", "Changed@456", "confirmPassword", "Changed@456"))
                .andExpect(status().isOk());

        doPost("/api/auth/login", null, Map.of("email", user.email(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login(user.email(), "Changed@456");
        // Token is single use; old refresh tokens were revoked.
        doPost("/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", "Again@7890", "confirmPassword", "Again@7890"))
                .andExpect(status().isBadRequest());
        doPost("/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken()))
                .andExpect(status().isUnauthorized());
    }
}
