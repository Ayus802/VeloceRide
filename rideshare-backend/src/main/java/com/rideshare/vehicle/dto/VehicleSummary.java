package com.rideshare.vehicle.dto;

import com.rideshare.common.enums.VehicleType;
import com.rideshare.vehicle.entity.Vehicle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Public vehicle info shown on rides (registration number is not exposed)")
public record VehicleSummary(UUID id, String vehicleName, VehicleType vehicleType, String brand, String model,
                             String color, boolean isVerified) {

    public static VehicleSummary from(Vehicle v) {
        return new VehicleSummary(v.getId(), v.getVehicleName(), v.getVehicleType(), v.getBrand(), v.getModel(),
                v.getColor(), v.isVerified());
    }
}
