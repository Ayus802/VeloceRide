package com.rideshare.ride.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Schema(description = "Publish or fully update a ride. The authenticated user is always the driver.")
public record RideRequest(
        @Schema(description = "One of your own vehicles") @NotNull UUID vehicleId,
        @Schema(example = "Delhi") @NotBlank @Size(max = 200) String source,
        @Schema(example = "Jaipur") @NotBlank @Size(max = 200) String destination,
        @Schema(example = "28.6139") @DecimalMin("-90.0") @DecimalMax("90.0") Double sourceLatitude,
        @Schema(example = "77.2090") @DecimalMin("-180.0") @DecimalMax("180.0") Double sourceLongitude,
        @Schema(example = "26.9124") @DecimalMin("-90.0") @DecimalMax("90.0") Double destinationLatitude,
        @Schema(example = "75.7873") @DecimalMin("-180.0") @DecimalMax("180.0") Double destinationLongitude,
        @Schema(example = "2026-11-15") @NotNull @FutureOrPresent LocalDate departureDate,
        @Schema(type = "string", example = "09:30") @JsonFormat(pattern = "H:mm[:ss]") @NotNull LocalTime departureTime,
        @Schema(example = "2026-11-15T14:30:00") LocalDateTime estimatedArrivalTime,
        @Schema(example = "3", description = "1-20 and not more than the vehicle's seating capacity")
        @NotNull @Min(1) @Max(20) Integer totalSeats,
        @Schema(example = "650.00") @NotNull @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 8, fraction = 2) BigDecimal pricePerSeat,
        @Schema(example = "AC car, one small bag per person") @Size(max = 1000) String description) {
}
