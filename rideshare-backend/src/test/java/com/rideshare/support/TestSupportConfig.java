package com.rideshare.support;

import com.rideshare.auth.service.PasswordResetNotifier;
import com.rideshare.user.entity.User;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class TestSupportConfig {

    /** Records verification codes instead of emailing/texting them, so tests can type them in. */
    public static class CapturingOtpDispatcher implements com.rideshare.verification.delivery.OtpDispatcher {
        private final Map<String, String> codes = new ConcurrentHashMap<>();
        private final Map<String, Integer> sends = new ConcurrentHashMap<>();

        @Override
        public boolean canDeliver(com.rideshare.common.enums.VerificationChannel channel) {
            return true;
        }

        @Override
        public void send(com.rideshare.common.enums.VerificationChannel channel, String destination, String name, String code) {
            codes.put(destination, code);
            sends.merge(destination, 1, Integer::sum);
        }

        /** Latest code sent to an email address or phone number. */
        public String codeFor(String destination) {
            String code = codes.get(destination);
            if (code == null) {
                throw new IllegalStateException("No code sent to " + destination);
            }
            return code;
        }

        public int sendCount(String destination) {
            return sends.getOrDefault(destination, 0);
        }
    }

    /**
     * Stands in for Google: "valid|sub|email|name" -> verified identity, "unverified|sub|email|name" -> email not
     * verified, anything else -> 401 (as the real verifier does for bad signatures/audience).
     */
    public static class FakeGoogleIdTokenVerifier implements com.rideshare.auth.google.GoogleIdTokenVerifier {
        @Override
        public GoogleIdentity verify(String idToken) {
            String[] p = idToken.split("\\|");
            if (p.length != 4 || !(p[0].equals("valid") || p[0].equals("unverified"))) {
                throw new com.rideshare.common.exception.UnauthorizedException("Google sign-in failed. Please try again.");
            }
            return new GoogleIdentity(p[1], p[2], p[0].equals("valid"), p[3], "https://lh3.googleusercontent.com/a/test");
        }
    }

    @Bean
    @Primary
    public CapturingOtpDispatcher capturingOtpDispatcher() {
        return new CapturingOtpDispatcher();
    }

    @Bean
    @Primary
    public FakeGoogleIdTokenVerifier fakeGoogleIdTokenVerifier() {
        return new FakeGoogleIdTokenVerifier();
    }


    /** Captures reset links instead of "sending" them, so tests can complete the reset flow. */
    public static class CapturingPasswordResetNotifier implements PasswordResetNotifier {
        private final Map<String, String> linksByEmail = new ConcurrentHashMap<>();

        @Override
        public void sendResetLink(User user, String resetLink) {
            linksByEmail.put(user.getEmail(), resetLink);
        }

        public Optional<String> tokenFor(String email) {
            return Optional.ofNullable(linksByEmail.get(email)).map(link -> link.substring(link.indexOf("token=") + 6));
        }

        public void clear() {
            linksByEmail.clear();
        }
    }

    @Bean
    @Primary
    public CapturingPasswordResetNotifier capturingPasswordResetNotifier() {
        return new CapturingPasswordResetNotifier();
    }
}
