package com.rideshare.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request seats on a ride. The authenticated user is always the passenger.")
public record CreateBookingRequest(
        @NotNull UUID rideId,
        @Schema(example = "2") @NotNull @Min(value = 1, message = "must be at least 1") @Max(20) Integer numberOfSeats) {
}
