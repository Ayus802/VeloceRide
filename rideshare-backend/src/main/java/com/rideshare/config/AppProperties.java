package com.rideshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Application settings bound from {@code app.*} (JWT settings live in {@link JwtConfig}).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timeZone,
        Cors cors,
        Admin admin,
        PasswordReset passwordReset,
        Vehicle vehicle,
        Ride ride,
        Location location,
        Payment payment,
        Otp otp,
        Mail mail,
        Sms sms,
        Google google) {

    public AppProperties {
        timeZone = (timeZone == null || timeZone.isBlank()) ? "UTC" : timeZone;
        cors = cors == null ? new Cors(List.of()) : cors;
        admin = admin == null ? new Admin(null, null, null, null) : admin;
        passwordReset = passwordReset == null ? new PasswordReset(null, null, false) : passwordReset;
        vehicle = vehicle == null ? new Vehicle(true) : vehicle;
        ride = ride == null ? new Ride(null) : ride;
        location = location == null ? new Location(null, true, null) : location;
        payment = payment == null ? new Payment("simulated") : payment;
        otp = otp == null ? new Otp(null, null, 0, null, 0, false) : otp;
        mail = mail == null ? new Mail(null, 0, null, null, true, null) : mail;
        sms = sms == null ? new Sms(null, null, null) : sms;
        google = google == null ? new Google(null) : google;
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                    .map(String::trim)
                    .filter(o -> !o.isEmpty() && !o.contains("${"))
                    .toList();
        }
    }

    public record Admin(String email, String password, String fullName, String phoneNumber) {
        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }

    public record PasswordReset(Duration tokenValidity, String frontendResetUrl, boolean logToken) {
        public PasswordReset {
            tokenValidity = tokenValidity == null ? Duration.ofMinutes(30) : tokenValidity;
        }
    }

    /** @param verificationRequired when true only admin-verified vehicles can be used to publish rides */
    public record Vehicle(boolean verificationRequired) {
    }

    /** @param startAllowedBeforeDeparture how early before departure a driver may start the ride */
    public record Ride(Duration startAllowedBeforeDeparture) {
        public Ride {
            startAllowedBeforeDeparture = startAllowedBeforeDeparture == null
                    ? Duration.ofHours(2) : startAllowedBeforeDeparture;
        }
    }

    /**
     * @param minUpdateInterval  minimum time between accepted location updates for one ride
     * @param historyEnabled     persist every accepted update in ride_locations
     * @param maxClientClockSkew how far a client timestamp may deviate from server time
     */
    public record Location(Duration minUpdateInterval, boolean historyEnabled, Duration maxClientClockSkew) {
        public Location {
            minUpdateInterval = minUpdateInterval == null ? Duration.ofSeconds(2) : minUpdateInterval;
            maxClientClockSkew = maxClientClockSkew == null ? Duration.ofMinutes(5) : maxClientClockSkew;
        }
    }

    public record Payment(String provider) {
    }

    /**
     * One-time verification codes.
     *
     * @param validity               how long a code can be used
     * @param resendCooldown         minimum time between two codes for the same channel
     * @param maxAttempts            wrong guesses allowed per code
     * @param challengeValidity      lifetime of the verification token returned to the client
     * @param maxSendsPerHour        codes per channel and user per rolling hour
     * @param logCodes               DEV ONLY: write codes to the log when no delivery provider is configured
     */
    public record Otp(Duration validity, Duration resendCooldown, int maxAttempts, Duration challengeValidity,
                      int maxSendsPerHour, boolean logCodes) {
        public Otp {
            validity = validity == null ? Duration.ofMinutes(10) : validity;
            resendCooldown = resendCooldown == null ? Duration.ofSeconds(60) : resendCooldown;
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
            challengeValidity = challengeValidity == null ? Duration.ofMinutes(30) : challengeValidity;
            maxSendsPerHour = maxSendsPerHour <= 0 ? 5 : maxSendsPerHour;
        }
    }

    /** SMTP settings for verification emails. Email delivery is enabled when {@code host} is set. */
    public record Mail(String host, int port, String username, String password, boolean starttls, String from) {
        public Mail {
            port = port <= 0 ? 587 : port;
            from = (from == null || from.isBlank()) ? "VeloceRide <no-reply@veloceride.local>" : from;
        }

        public boolean isConfigured() {
            return host != null && !host.isBlank();
        }
    }

    /** Twilio credentials for verification SMS. SMS delivery is enabled when all three are set. */
    public record Sms(String twilioAccountSid, String twilioAuthToken, String twilioFromNumber) {
        public boolean isConfigured() {
            return notBlank(twilioAccountSid) && notBlank(twilioAuthToken) && notBlank(twilioFromNumber);
        }
    }

    /** Google Sign-In OAuth client id (the ID token audience). Google sign-in is disabled when blank. */
    public record Google(String clientId) {
        public boolean isConfigured() {
            return notBlank(clientId);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
