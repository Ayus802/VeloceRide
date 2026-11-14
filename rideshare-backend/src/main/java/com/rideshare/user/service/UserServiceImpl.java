package com.rideshare.user.service;

import com.rideshare.auth.service.RefreshTokenService;
import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.exception.BadRequestException;
import com.rideshare.common.exception.DuplicateResourceException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.user.dto.ActiveModeRequest;
import com.rideshare.user.dto.ChangePasswordRequest;
import com.rideshare.user.dto.PublicUserResponse;
import com.rideshare.user.dto.UpdateProfileRequest;
import com.rideshare.user.dto.UserProfileResponse;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        return userMapper.toProfile(load(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserResponse getPublicProfile(UUID userId) {
        User user = load(userId);
        if (!user.isActive()) {
            throw ResourceNotFoundException.of("User");
        }
        return userMapper.toPublic(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        String phone = request.phoneNumber().trim();
        if (userRepository.existsByPhoneNumberAndIdNot(phone, userId)) {
            throw new DuplicateResourceException("Phone number is already registered");
        }
        user.setFullName(request.fullName().trim());
        if (!phone.equals(user.getPhoneNumber())) {
            // A new number must be confirmed again (POST /api/users/me/verification/PHONE/send).
            user.setPhoneVerified(false);
        }
        user.setPhoneNumber(phone);
        user.setProfilePicture(blankToNull(request.profilePicture()));
        userRepository.saveAndFlush(user);
        return userMapper.toProfile(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(userId); // other devices must sign in again
    }

    @Override
    @Transactional
    public UserProfileResponse switchActiveMode(UUID userId, ActiveModeRequest request) {
        User user = load(userId);
        user.setActiveMode(request.activeMode()); // UI preference only - grants/removes no permissions
        userRepository.saveAndFlush(user);
        return userMapper.toProfile(user);
    }

    @Override
    @Transactional
    public void deactivateOwnAccount(UUID userId) {
        User user = load(userId);
        user.setAccountStatus(AccountStatus.DEACTIVATED);
        refreshTokenService.revokeAll(userId);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
