package com.rideshare.user.service;

import com.rideshare.user.dto.ActiveModeRequest;
import com.rideshare.user.dto.ChangePasswordRequest;
import com.rideshare.user.dto.PublicUserResponse;
import com.rideshare.user.dto.UpdateProfileRequest;
import com.rideshare.user.dto.UserProfileResponse;

import java.util.UUID;

public interface UserService {

    UserProfileResponse getProfile(UUID userId);

    PublicUserResponse getPublicProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    void changePassword(UUID userId, ChangePasswordRequest request);

    UserProfileResponse switchActiveMode(UUID userId, ActiveModeRequest request);

    void deactivateOwnAccount(UUID userId);
}
