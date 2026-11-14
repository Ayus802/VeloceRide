package com.rideshare.ride.dto;

import com.rideshare.common.enums.RideStatus;
import com.rideshare.vehicle.dto.VehicleSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Schema(description = "Ride with driver and vehicle details")
public record RideResponse(
        UUID id,
        String source,
        String destination,
        Double sourceLatitude,
        Double sourceLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        LocalDate departureDate,
        @Schema(type = "string", example = "09:30:00") LocalTime departureTime,
        LocalDateTime estimatedArrivalTime,
        int totalSeats,
        int availableSeats,
        int bookedSeats,
        BigDecimal pricePerSeat,
        String description,
        RideStatus rideStatus,
        DriverSummary driver,
        VehicleSummary vehicle,
        Instant createdAt,
        Instant updatedAt) {
}
