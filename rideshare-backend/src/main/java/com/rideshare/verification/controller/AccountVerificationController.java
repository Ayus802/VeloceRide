package com.rideshare.verification.controller;

import com.rideshare.common.enums.VerificationChannel;
import com.rideshare.common.response.ApiResponse;
import com.rideshare.security.UserPrincipal;
import com.rideshare.user.dto.UserProfileResponse;
import com.rideshare.user.service.UserMapper;
import com.rideshare.verification.dto.VerificationDtos.ChannelStatus;
import com.rideshare.verification.dto.VerificationDtos.CodeOnlyRequest;
import com.rideshare.verification.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Re-verification for signed-in users, e.g. after changing the phone number on the profile. */
@RestController
@RequestMapping("/api/users/me/verification")
@RequiredArgsConstructor
@Tag(name = "2. Users")
public class AccountVerificationController {

    private final VerificationService verificationService;
    private final UserMapper userMapper;

    @PostMapping("/{channel}/send")
    @Operation(summary = "Send a verification code to my email or phone",
            description = "400 if already verified; 429 during cooldown or after the hourly limit.")
    public ResponseEntity<ApiResponse<ChannelStatus>> send(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable VerificationChannel channel) {
        return ResponseEntity.ok(ApiResponse.success("Verification code sent",
                verificationService.sendForUser(principal.id(), channel)));
    }

    @PostMapping("/{channel}/confirm")
    @Operation(summary = "Confirm my email or phone with the code", description = "400 wrong or expired code.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable VerificationChannel channel,
                                                                    @Valid @RequestBody CodeOnlyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Verified",
                userMapper.toProfile(verificationService.confirmForUser(principal.id(), channel, request.code()))));
    }
}
