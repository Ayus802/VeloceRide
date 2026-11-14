package com.rideshare.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VerificationAndGoogleIntegrationTest extends AbstractIntegrationTest {

    private String startRegistration(String email, String phone) throws Exception {
        JsonNode flow = data(doPost("/api/auth/register", null, registration(email, phone)).andExpect(status().isCreated()));
        assertThat(flow.get("status").asText()).isEqualTo("VERIFICATION_REQUIRED");
        assertThat(flow.at("/verification/channels/0/destination").asText()).doesNotContain(email); // masked
        return flow.at("/verification/verificationToken").asText();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    // ------------------------------------------------------------------ email + phone OTP

    @Test
    void registrationSendsCodesAndIssuesTokensOnlyAfterBothAreConfirmed() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        String token = startRegistration(email, phone);
        assertThat(otp.sendCount(email)).isEqualTo(1);
        assertThat(otp.sendCount(phone)).isEqualTo(1);

        confirmCode(token, "EMAIL", otp.codeFor(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.data.verification.channels[0].verified").value(true))
                .andExpect(jsonPath("$.data.verification.channels[1].verified").value(false));

        confirmCode(token, "PHONE", otp.codeFor(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.auth.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.auth.user.emailVerified").value(true))
                .andExpect(jsonPath("$.data.auth.user.phoneVerified").value(true));

        login(email, PASSWORD); // verified accounts log in normally
    }

    @Test
    void loginBeforeVerificationReturnsChallengeWithoutResendingRecentCodes() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        startRegistration(email, phone);

        doPost("/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.status").value("VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.data.verification.verificationToken", notNullValue()))
                .andExpect(jsonPath("$.data.auth").doesNotExist());
        assertThat(otp.sendCount(email)).isEqualTo(1); // still-valid code was not re-sent
    }

    @Test
    void wrongCodesAreLimitedAndConsumeTheCode() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        String token = startRegistration(email, phone);
        String good = otp.codeFor(email);

        confirmCode(token, "EMAIL", wrong(good))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incorrect code. 4 attempts left."));
        for (int i = 0; i < 3; i++) {
            confirmCode(token, "EMAIL", wrong(good)).andExpect(status().isBadRequest());
        }
        confirmCode(token, "EMAIL", wrong(good))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Too many incorrect attempts. Request a new code."));
        // The right code no longer works after the limit.
        confirmCode(token, "EMAIL", good)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No active code. Request a new one."));
    }

    @Test
    void resendIsRateLimitedAndReplacesThePreviousCode() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        String token = startRegistration(email, phone);
        String first = otp.codeFor(email);

        doPost("/api/auth/verification/resend", null, Map.of("verificationToken", token, "channel", "EMAIL"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Please wait")));

        Thread.sleep(1_100); // test profile cooldown is 1 s
        doPost("/api/auth/verification/resend", null, Map.of("verificationToken", token, "channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verification.channels[0].codeSent").value(true));
        assertThat(otp.sendCount(email)).isEqualTo(2);
        String second = otp.codeFor(email);

        if (!first.equals(second)) {
            confirmCode(token, "EMAIL", first).andExpect(status().isBadRequest()); // old code invalidated
        }
        confirmCode(token, "EMAIL", second).andExpect(status().isOk());
    }

    @Test
    void invalidVerificationTokenIsRejected() throws Exception {
        confirmCode("not-a-token", "EMAIL", "123456")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("expired")));
        confirmCode("x", "EMAIL", "12").andExpect(status().isBadRequest()); // code must be 6 digits
    }

    @Test
    void changingThePhoneNumberRequiresReverification() throws Exception {
        var user = registerUser();
        String newPhone = uniquePhone();
        doPut("/api/users/me", user, Map.of("fullName", "Test User", "phoneNumber", newPhone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneVerified").value(false));

        doPost("/api/users/me/verification/PHONE/send", user, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codeSent").value(true));
        doPost("/api/users/me/verification/PHONE/confirm", user, Map.of("code", otp.codeFor(newPhone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneVerified").value(true));
        doPost("/api/users/me/verification/EMAIL/send", user, null).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ Google

    @Test
    void newGoogleUserAddsAndVerifiesPhoneThenSignsInDirectly() throws Exception {
        String email = uniqueEmail();
        String sub = "g-" + email;
        String idToken = "valid|" + sub + "|" + email + "|Gita Rao";

        JsonNode first = data(doPost("/api/auth/google", null, Map.of("idToken", idToken)).andExpect(status().isOk()));
        assertThat(first.get("status").asText()).isEqualTo("PHONE_REQUIRED");
        assertThat(first.at("/googleSignup/email").asText()).isEqualTo(email);
        assertThat(first.at("/googleSignup/fullName").asText()).isEqualTo("Gita Rao");

        String phone = uniquePhone();
        JsonNode created = data(doPost("/api/auth/google/complete", null,
                Map.of("signupToken", first.at("/googleSignup/signupToken").asText(), "phoneNumber", phone))
                .andExpect(status().isCreated()));
        assertThat(created.get("status").asText()).isEqualTo("VERIFICATION_REQUIRED");
        assertThat(created.at("/verification/channels/0/verified").asBoolean()).isTrue();  // email: by Google
        assertThat(created.at("/verification/channels/1/verified").asBoolean()).isFalse(); // phone: pending
        assertThat(otp.sendCount(email)).isZero();

        JsonNode done = data(confirmCode(created.at("/verification/verificationToken").asText(), "PHONE",
                otp.codeFor(phone)).andExpect(status().isOk()));
        assertThat(done.get("status").asText()).isEqualTo("AUTHENTICATED");
        assertThat(done.at("/auth/user/googleLinked").asBoolean()).isTrue();
        assertThat(done.at("/auth/user/passwordSet").asBoolean()).isFalse();

        doPost("/api/auth/google", null, Map.of("idToken", idToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.auth.accessToken", notNullValue()));

        // Google-only accounts have no password.
        doPost("/api/auth/login", null, Map.of("email", email, "password", PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void googleSignInLinksAnExistingAccountWithTheSameVerifiedEmail() throws Exception {
        var user = registerUser();
        doPost("/api/auth/google", null, Map.of("idToken", "valid|g-link-" + user.id() + "|" + user.email() + "|Test User"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.data.auth.user.id").value(user.id().toString()))
                .andExpect(jsonPath("$.data.auth.user.googleLinked").value(true))
                .andExpect(jsonPath("$.data.auth.user.passwordSet").value(true));
        login(user.email(), PASSWORD); // password still works
    }

    @Test
    void googleRejectsInvalidTokensAndUnverifiedEmails() throws Exception {
        doPost("/api/auth/google", null, Map.of("idToken", "forged"))
                .andExpect(status().isUnauthorized());
        doPost("/api/auth/google", null, Map.of("idToken", "unverified|g-x|" + uniqueEmail() + "|X Y"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your Google account email is not verified"));
        doPost("/api/auth/google/complete", null, Map.of("signupToken", "bogus", "phoneNumber", uniquePhone()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleSignupCannotReuseARegisteredPhone() throws Exception {
        String phone = uniquePhone();
        startRegistration(uniqueEmail(), phone);
        String email = uniqueEmail();
        JsonNode first = data(doPost("/api/auth/google", null, Map.of("idToken", "valid|g-" + email + "|" + email + "|Ann Lee")));
        doPost("/api/auth/google/complete", null,
                Map.of("signupToken", first.at("/googleSignup/signupToken").asText(), "phoneNumber", phone))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phone number is already registered"));
    }
}
