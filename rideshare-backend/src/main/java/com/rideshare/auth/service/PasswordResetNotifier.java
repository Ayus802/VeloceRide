package com.rideshare.auth.service;

import com.rideshare.user.entity.User;

/**
 * Delivers password reset links. Replace {@link LoggingPasswordResetNotifier} with an email/SMS
 * implementation annotated with {@code @Primary} for production.
 */
public interface PasswordResetNotifier {

    void sendResetLink(User user, String resetLink);
}
