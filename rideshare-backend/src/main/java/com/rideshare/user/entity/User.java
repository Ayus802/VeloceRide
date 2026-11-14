package com.rideshare.user.entity;

import com.rideshare.common.entity.BaseEntity;
import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.enums.ActiveMode;
import com.rideshare.common.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single account type. The same user (same id, email, phone, password) publishes rides as a driver
 * and books rides as a passenger. {@link ActiveMode} is only a UI preference, never an authorization input.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    /** BCrypt hash; null for accounts created through Google sign-in until a password is set. */
    @Column(name = "password", length = 100)
    private String password;

    @Column(name = "profile_picture", length = 1000)
    private String profilePicture;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_mode", nullable = false, length = 20)
    private ActiveMode activeMode = ActiveMode.PASSENGER;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    /** Google account id ("sub") when the account is linked to Google sign-in. */
    @Column(name = "google_subject", length = 64)
    private String googleSubject;

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /** Both the email address and the phone number have been confirmed with a one-time code. */
    public boolean isFullyVerified() {
        return emailVerified && phoneVerified;
    }
}
