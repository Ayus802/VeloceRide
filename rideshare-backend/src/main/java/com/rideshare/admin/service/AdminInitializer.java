package com.rideshare.admin.service;

import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.enums.ActiveMode;
import com.rideshare.common.enums.Role;
import com.rideshare.common.validation.ValidationPatterns;
import com.rideshare.config.AppProperties;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the initial ADMIN account from ADMIN_EMAIL / ADMIN_PASSWORD on startup, if it does not exist yet.
 * Nothing is created when the variables are absent; no credentials are hard-coded for production.
 * An existing non-admin account with that email is never silently promoted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.admin();
        if (!admin.isConfigured()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set; skipping initial admin creation");
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(email).ifPresentOrElse(existing -> {
            if (existing.getRole() != Role.ADMIN) {
                log.warn("User {} exists but is not an ADMIN; not promoting automatically", existing.getId());
            }
        }, () -> {
            if (!admin.password().matches(ValidationPatterns.PASSWORD)) {
                throw new IllegalStateException("ADMIN_PASSWORD does not meet the password policy: "
                        + ValidationPatterns.PASSWORD_MESSAGE);
            }
            User user = new User();
            user.setEmail(email);
            user.setFullName(admin.fullName() == null || admin.fullName().isBlank() ? "Platform Admin" : admin.fullName());
            user.setPhoneNumber(admin.phoneNumber() == null || admin.phoneNumber().isBlank()
                    ? "+10000000000" : admin.phoneNumber());
            user.setPassword(passwordEncoder.encode(admin.password()));
            user.setRole(Role.ADMIN);
            // Bootstrapped from trusted configuration, not self-registration.
            user.setEmailVerified(true);
            user.setPhoneVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setActiveMode(ActiveMode.PASSENGER);
            userRepository.save(user);
            log.info("Initial admin account created for {}", email);
        });
    }
}
