package com.rideshare.booking.dto;

import com.rideshare.common.enums.RideStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Schema(description = "Ride info embedded in a booking")
public record BookingRideInfo(UUID id, String source, String destination, LocalDate departureDate,
                              @Schema(type = "string", example = "09:30:00") LocalTime departureTime,
                              RideStatus rideStatus, BigDecimal pricePerSeat) {
}
