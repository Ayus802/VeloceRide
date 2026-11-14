package com.rideshare.admin.dto;

import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.enums.ActiveMode;
import com.rideshare.common.enums.Role;
import com.rideshare.user.entity.User;

import java.time.Instant;
import java.util.UUID;

/** Admin view of an account (never includes the password hash). */
public record AdminUserResponse(UUID id, String fullName, String email, String phoneNumber, String profilePicture,
                                Role role, AccountStatus accountStatus, ActiveMode activeMode,
                                boolean emailVerified, boolean phoneVerified,
                                Instant createdAt, Instant updatedAt) {

    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(),
                u.getProfilePicture(), u.getRole(), u.getAccountStatus(), u.getActiveMode(),
                u.isEmailVerified(), u.isPhoneVerified(),
                u.getCreatedAt(), u.getUpdatedAt());
    }
}
