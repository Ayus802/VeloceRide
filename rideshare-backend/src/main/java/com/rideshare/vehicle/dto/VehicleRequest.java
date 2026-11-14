package com.rideshare.vehicle.dto;

import com.rideshare.common.enums.VehicleType;
import com.rideshare.common.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Create or replace a vehicle. Changing the number or document resets verification to PENDING.")
public record VehicleRequest(
        @Schema(example = "My Swift") @NotBlank @Size(max = 100) String vehicleName,
        @Schema(example = "DL 01 AB 1234", description = "Stored upper-case without spaces/dashes; must be unique")
        @NotBlank @Pattern(regexp = ValidationPatterns.VEHICLE_NUMBER,
                message = "must be 4-20 letters, digits, spaces or dashes") String vehicleNumber,
        @Schema(example = "HATCHBACK") @NotNull VehicleType vehicleType,
        @Schema(example = "Maruti Suzuki") @NotBlank @Size(max = 50) String brand,
        @Schema(example = "Swift") @NotBlank @Size(max = 50) String model,
        @Schema(example = "White") @NotBlank @Size(max = 30) String color,
        @Schema(example = "4", description = "Passenger seats (driver excluded), 1-20")
        @NotNull @Min(1) @Max(20) Integer seatingCapacity,
        @Schema(example = "https://cdn.example.com/docs/rc.pdf")
        @Size(max = 1000) @URL(regexp = "^https?://.*", message = "must be a valid http(s) URL") String registrationDocumentUrl) {
}
