package com.rideshare.ride.controller;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.response.ApiResponse;
import com.rideshare.common.response.PageResponse;
import com.rideshare.ride.dto.RideRequest;
import com.rideshare.ride.dto.RideResponse;
import com.rideshare.ride.service.RideService;
import com.rideshare.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
@Tag(name = "4. Rides", description = "Publish, manage, search and run rides. Any account can publish (driver) "
        + "and book (passenger); ownership is enforced per ride.")
public class RideController {

    private final RideService rideService;

    @PostMapping
    @Operation(summary = "Publish a ride", description = "The logged-in user becomes the driver. The vehicle must be "
            + "owned by the caller (403) and verified when verification is enabled (422).")
    public ResponseEntity<ApiResponse<RideResponse>> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                             @Valid @RequestBody RideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ride published successfully", rideService.publish(principal.id(), request)));
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Browse upcoming rides (public)", description = "Scheduled future rides with free seats.")
    public ResponseEntity<ApiResponse<PageResponse<RideResponse>>> upcoming(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Rides retrieved", rideService.listUpcoming(pageable)));
    }

    @GetMapping("/search")
    @SecurityRequirements
    @Operation(summary = "Search rides by source, destination and journey date (public)",
            description = "Case-insensitive location matching. Returns only SCHEDULED, future rides with available "
                    + "seats. Sortable by departureTime (default), pricePerSeat, availableSeats, createdAt. "
                    + "Example: /api/rides/search?source=Delhi&destination=Jaipur&date=2026-11-15&sort=pricePerSeat,asc")
    public ResponseEntity<ApiResponse<PageResponse<RideResponse>>> search(
            @Parameter(example = "Delhi") @RequestParam @NotBlank @Size(max = 200) String source,
            @Parameter(example = "Jaipur") @RequestParam @NotBlank @Size(max = 200) String destination,
            @Parameter(example = "2026-11-15") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Rides retrieved",
                rideService.search(source, destination, date, pageable)));
    }

    @GetMapping("/my-rides")
    @Operation(summary = "Rides I published as a driver", description = "Optional status filter.")
    public ResponseEntity<ApiResponse<PageResponse<RideResponse>>> myRides(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) RideStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Rides retrieved",
                rideService.getMyRides(principal.id(), status, pageable)));
    }

    @GetMapping("/{rideId}")
    @SecurityRequirements
    @Operation(summary = "Get ride details (public)")
    public ResponseEntity<ApiResponse<RideResponse>> get(@PathVariable UUID rideId) {
        return ResponseEntity.ok(ApiResponse.success("Ride retrieved", rideService.getRide(rideId)));
    }

    @PutMapping("/{rideId}")
    @Operation(summary = "Update a scheduled ride", description = "Driver only (403). Route/schedule/price/vehicle are "
            + "locked once bookings exist (409). Seats cannot drop below booked seats (422).")
    public ResponseEntity<ApiResponse<RideResponse>> update(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID rideId,
                                                            @Valid @RequestBody RideRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ride updated", rideService.update(principal.id(), rideId, request)));
    }

    @DeleteMapping("/{rideId}")
    @Operation(summary = "Cancel a scheduled ride", description = "Driver only. All pending/accepted bookings are "
            + "cancelled, passengers are notified and successful payments are refunded.")
    public ResponseEntity<ApiResponse<RideResponse>> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID rideId) {
        return ResponseEntity.ok(ApiResponse.success("Ride cancelled", rideService.cancel(principal.id(), rideId)));
    }

    @PatchMapping("/{rideId}/start")
    @Operation(summary = "Start the ride (SCHEDULED -> ONGOING)", description = "Driver only. Enables live location "
            + "tracking; unanswered requests are auto-rejected; accepted passengers are notified.")
    public ResponseEntity<ApiResponse<RideResponse>> start(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID rideId) {
        return ResponseEntity.ok(ApiResponse.success("Ride started", rideService.start(principal.id(), rideId)));
    }

    @PatchMapping("/{rideId}/complete")
    @Operation(summary = "Complete the ride (ONGOING -> COMPLETED)", description = "Driver only. Stops tracking; "
            + "accepted bookings become COMPLETED and participants can review each other.")
    public ResponseEntity<ApiResponse<RideResponse>> complete(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable UUID rideId) {
        return ResponseEntity.ok(ApiResponse.success("Ride completed", rideService.complete(principal.id(), rideId)));
    }
}
