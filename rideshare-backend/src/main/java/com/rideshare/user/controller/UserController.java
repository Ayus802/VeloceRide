package com.rideshare.user.controller;

import com.rideshare.common.response.ApiResponse;
import com.rideshare.security.UserPrincipal;
import com.rideshare.user.dto.ActiveModeRequest;
import com.rideshare.user.dto.ChangePasswordRequest;
import com.rideshare.user.dto.PublicUserResponse;
import com.rideshare.user.dto.UpdateProfileRequest;
import com.rideshare.user.dto.UserProfileResponse;
import com.rideshare.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "2. Users", description = "Unified profile (one account, two modes) and mode switching")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get the logged-in user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", userService.getProfile(principal.id())));
    }

    @PutMapping("/me")
    @Operation(summary = "Update profile (name, phone, profile picture URL)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> update(@AuthenticationPrincipal UserPrincipal principal,
                                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                userService.updateProfile(principal.id(), request)));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Change password", description = "Requires the current password. Signs out other sessions.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed"));
    }

    @PatchMapping("/active-mode")
    @Operation(summary = "Switch between RIDER and PASSENGER mode",
            description = "Stores the UI preference. It does NOT restrict actions: a user in PASSENGER mode can still "
                    + "publish rides and a user in RIDER mode can still book rides with the same account.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> switchMode(@AuthenticationPrincipal UserPrincipal principal,
                                                                       @Valid @RequestBody ActiveModeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Active mode switched to " + request.activeMode(),
                userService.switchActiveMode(principal.id(), request)));
    }

    @PatchMapping("/me/deactivate")
    @Operation(summary = "Deactivate own account", description = "Revokes all sessions. Reactivation is done by an admin.")
    public ResponseEntity<ApiResponse<Void>> deactivate(@AuthenticationPrincipal UserPrincipal principal) {
        userService.deactivateOwnAccount(principal.id());
        return ResponseEntity.ok(ApiResponse.success("Account deactivated"));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get another user's public profile", description = "No email or phone number is exposed.")
    public ResponseEntity<ApiResponse<PublicUserResponse>> publicProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("User retrieved", userService.getPublicProfile(userId)));
    }
}
