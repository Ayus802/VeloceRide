package com.rideshare.vehicle.controller;

import com.rideshare.common.response.ApiResponse;
import com.rideshare.common.response.PageResponse;
import com.rideshare.security.UserPrincipal;
import com.rideshare.vehicle.dto.VehicleRequest;
import com.rideshare.vehicle.dto.VehicleResponse;
import com.rideshare.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "3. Vehicles", description = "Register and manage the vehicles you drive. Only the owner can modify a vehicle.")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @Operation(summary = "Add a vehicle", description = "New vehicles start as PENDING verification. 409 if the number exists.")
    public ResponseEntity<ApiResponse<VehicleResponse>> add(@AuthenticationPrincipal UserPrincipal principal,
                                                            @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle added", vehicleService.addVehicle(principal.id(), request)));
    }

    @GetMapping
    @Operation(summary = "List my vehicles", description = "Sortable by createdAt, vehicleName, verificationStatus.")
    public ResponseEntity<ApiResponse<PageResponse<VehicleResponse>>> mine(
            @AuthenticationPrincipal UserPrincipal principal, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Vehicles retrieved",
                vehicleService.getMyVehicles(principal.id(), pageable)));
    }

    @GetMapping("/{vehicleId}")
    @Operation(summary = "Get a vehicle", description = "Owner or admin only.")
    public ResponseEntity<ApiResponse<VehicleResponse>> get(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID vehicleId) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle retrieved", vehicleService.getVehicle(principal, vehicleId)));
    }

    @PutMapping("/{vehicleId}")
    @Operation(summary = "Update a vehicle", description = "Owner only (403 otherwise).")
    public ResponseEntity<ApiResponse<VehicleResponse>> update(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID vehicleId,
                                                               @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle updated",
                vehicleService.updateVehicle(principal.id(), vehicleId, request)));
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Delete a vehicle", description = "Owner only. 409 if it has scheduled/ongoing rides.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID vehicleId) {
        vehicleService.deleteVehicle(principal.id(), vehicleId);
        return ResponseEntity.noContent().build();
    }
}
