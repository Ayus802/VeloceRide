package com.rideshare.vehicle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectVehicleRequest(
        @Schema(example = "Registration document is unreadable") @NotBlank @Size(max = 500) String reason) {
}
