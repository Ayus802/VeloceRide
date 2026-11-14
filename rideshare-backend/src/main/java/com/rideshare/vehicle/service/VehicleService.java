package com.rideshare.vehicle.service;

import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.common.response.PageResponse;
import com.rideshare.security.UserPrincipal;
import com.rideshare.vehicle.dto.VehicleRequest;
import com.rideshare.vehicle.dto.VehicleResponse;
import com.rideshare.vehicle.entity.Vehicle;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface VehicleService {

    VehicleResponse addVehicle(UUID ownerId, VehicleRequest request);

    VehicleResponse updateVehicle(UUID ownerId, UUID vehicleId, VehicleRequest request);

    void deleteVehicle(UUID ownerId, UUID vehicleId);

    PageResponse<VehicleResponse> getMyVehicles(UUID ownerId, Pageable pageable);

    VehicleResponse getVehicle(UserPrincipal caller, UUID vehicleId);

    /** Admin: list vehicles, optionally filtered by verification status. */
    PageResponse<VehicleResponse> listForVerification(VerificationStatus status, Pageable pageable);

    VehicleResponse verify(UUID vehicleId);

    VehicleResponse reject(UUID vehicleId, String reason);

    /**
     * Loads a vehicle the given user may use to publish a ride: owned by them, not deleted and,
     * when verification is required, verified.
     */
    Vehicle requireUsableVehicle(UUID ownerId, UUID vehicleId);
}
