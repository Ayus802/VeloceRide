package com.rideshare.location.controller;

import com.rideshare.common.response.ApiResponse;
import com.rideshare.common.response.PageResponse;
import com.rideshare.location.dto.LocationPoint;
import com.rideshare.location.dto.LocationResponse;
import com.rideshare.location.dto.LocationUpdateRequest;
import com.rideshare.location.service.LocationService;
import com.rideshare.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rides/{rideId}/location")
@RequiredArgsConstructor
@Tag(name = "9. Live Location", description = "REST companions to the STOMP live-tracking channel. "
        + "See docs/WEBSOCKET_API.md for /ws, /app/location/update and /topic/rides/{rideId}/location.")
public class LocationController {

    private final LocationService locationService;

    @PostMapping
    @Operation(summary = "Publish a location update (REST fallback for drivers)",
            description = "Same rules as the WebSocket channel: driver only (403), ride must be ONGOING (409), "
                    + "valid coordinates (400), rate limited (429). Broadcast to subscribers.")
    public ResponseEntity<ApiResponse<LocationResponse>> publish(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable UUID rideId,
                                                                 @Valid @RequestBody LocationPoint point) {
        LocationResponse response = locationService.publishUpdate(principal.id(),
                new LocationUpdateRequest(rideId, point.latitude(), point.longitude(), point.timestamp()));
        return ResponseEntity.ok(ApiResponse.success("Location updated", response));
    }

    @GetMapping("/latest")
    @Operation(summary = "Latest driver location", description = "Driver or passengers with an ACCEPTED booking only.")
    public ResponseEntity<ApiResponse<LocationResponse>> latest(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable UUID rideId) {
        return ResponseEntity.ok(ApiResponse.success("Location retrieved", locationService.getLatest(principal.id(), rideId)));
    }

    @GetMapping("/history")
    @Operation(summary = "Location history of a ride", description = "Driver or accepted/completed passengers only.")
    public ResponseEntity<ApiResponse<PageResponse<LocationResponse>>> history(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID rideId,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Location history retrieved",
                locationService.getHistory(principal.id(), rideId, pageable)));
    }
}
