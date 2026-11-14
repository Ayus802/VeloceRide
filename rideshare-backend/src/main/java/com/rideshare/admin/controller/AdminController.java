package com.rideshare.admin.controller;

import com.rideshare.admin.dto.AdminReviewResponse;
import com.rideshare.admin.dto.AdminUserResponse;
import com.rideshare.admin.dto.ModerateReviewRequest;
import com.rideshare.admin.dto.PlatformStatsResponse;
import com.rideshare.admin.dto.UpdateAccountStatusRequest;
import com.rideshare.admin.service.AdminService;
import com.rideshare.booking.dto.BookingResponse;
import com.rideshare.booking.service.BookingService;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.common.response.ApiResponse;
import com.rideshare.common.response.PageResponse;
import com.rideshare.ride.dto.RideResponse;
import com.rideshare.ride.service.RideService;
import com.rideshare.security.UserPrincipal;
import com.rideshare.vehicle.dto.RejectVehicleRequest;
import com.rideshare.vehicle.dto.VehicleResponse;
import com.rideshare.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Every endpoint requires ROLE_ADMIN (enforced both by URL rule and method security). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "10. Admin", description = "Platform administration (ADMIN role only)")
public class AdminController {

    private final AdminService adminService;
    private final VehicleService vehicleService;
    private final RideService rideService;
    private final BookingService bookingService;

    @GetMapping("/users")
    @Operation(summary = "List users", description = "Optional q searches name, email and phone.")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> users(
            @RequestParam(required = false) String q, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", adminService.listUsers(q, pageable)));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user details")
    public ResponseEntity<ApiResponse<AdminUserResponse>> user(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("User retrieved", adminService.getUser(userId)));
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Activate or deactivate an account", description = "Deactivation revokes all sessions.")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                                       @PathVariable UUID userId,
                                                                       @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Account status updated",
                adminService.updateAccountStatus(principal.id(), userId, request.accountStatus())));
    }

    @GetMapping("/rides")
    @Operation(summary = "List all rides", description = "Optional status filter.")
    public ResponseEntity<ApiResponse<PageResponse<RideResponse>>> rides(
            @RequestParam(required = false) RideStatus status, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Rides retrieved", rideService.listAll(status, pageable)));
    }

    @GetMapping("/bookings")
    @Operation(summary = "List all bookings", description = "Optional status filter.")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> bookings(
            @RequestParam(required = false) BookingStatus status, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Bookings retrieved", bookingService.listAll(status, pageable)));
    }

    @GetMapping("/vehicles")
    @Operation(summary = "List vehicles for verification", description = "Defaults to PENDING; pass status to change.")
    public ResponseEntity<ApiResponse<PageResponse<VehicleResponse>>> vehicles(
            @RequestParam(required = false, defaultValue = "PENDING") VerificationStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Vehicles retrieved",
                vehicleService.listForVerification(status, pageable)));
    }

    @PatchMapping("/vehicles/{vehicleId}/verify")
    @Operation(summary = "Verify a vehicle")
    public ResponseEntity<ApiResponse<VehicleResponse>> verify(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle verified", vehicleService.verify(vehicleId)));
    }

    @PatchMapping("/vehicles/{vehicleId}/reject")
    @Operation(summary = "Reject a vehicle with a reason")
    public ResponseEntity<ApiResponse<VehicleResponse>> reject(@PathVariable UUID vehicleId,
                                                               @Valid @RequestBody RejectVehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle rejected", vehicleService.reject(vehicleId, request.reason())));
    }

    @GetMapping("/reviews")
    @Operation(summary = "List reviews", description = "Optional hidden=true|false filter.")
    public ResponseEntity<ApiResponse<PageResponse<AdminReviewResponse>>> reviews(
            @RequestParam(required = false) Boolean hidden, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved", adminService.listReviews(hidden, pageable)));
    }

    @PatchMapping("/reviews/{reviewId}/moderate")
    @Operation(summary = "Hide or restore a review", description = "Hidden reviews are excluded from ratings.")
    public ResponseEntity<ApiResponse<AdminReviewResponse>> moderate(@PathVariable UUID reviewId,
                                                                     @Valid @RequestBody ModerateReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Review moderated", adminService.moderateReview(reviewId, request)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Platform statistics")
    public ResponseEntity<ApiResponse<PlatformStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved", adminService.stats()));
    }
}
