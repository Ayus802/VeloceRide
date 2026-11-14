package com.rideshare.auth.service;

import com.rideshare.config.AppProperties;
import com.rideshare.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback notifier used until an email provider is plugged in. The reset link is only written to the log
 * when {@code app.password-reset.log-token=true} (enabled in the dev profile, disabled in prod).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private final AppProperties properties;

    @Override
    public void sendResetLink(User user, String resetLink) {
        if (properties.passwordReset().logToken()) {
            log.info("[DEV ONLY] Password reset link for user {}: {}", user.getId(), resetLink);
        } else {
            log.info("Password reset requested for user {} (no delivery channel configured)", user.getId());
        }
    }
}
