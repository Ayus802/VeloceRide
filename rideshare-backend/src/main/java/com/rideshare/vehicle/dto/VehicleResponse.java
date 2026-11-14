package com.rideshare.vehicle.dto;

import com.rideshare.common.enums.VehicleType;
import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.vehicle.entity.Vehicle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Full vehicle details, visible to the owner and admins")
public record VehicleResponse(
        UUID id,
        UUID ownerId,
        String vehicleName,
        String vehicleNumber,
        VehicleType vehicleType,
        String brand,
        String model,
        String color,
        Integer seatingCapacity,
        String registrationDocumentUrl,
        VerificationStatus verificationStatus,
        boolean isVerified,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {

    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getOwner().getId(), v.getVehicleName(), v.getVehicleNumber(),
                v.getVehicleType(), v.getBrand(), v.getModel(), v.getColor(), v.getSeatingCapacity(),
                v.getRegistrationDocumentUrl(), v.getVerificationStatus(), v.isVerified(), v.getRejectionReason(),
                v.getCreatedAt(), v.getUpdatedAt());
    }
}
