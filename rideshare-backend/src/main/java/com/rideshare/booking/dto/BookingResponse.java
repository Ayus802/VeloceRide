package com.rideshare.booking.dto;

import com.rideshare.common.enums.BookingStatus;
import com.rideshare.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Booking as seen by its passenger, the ride's driver or an admin")
public record BookingResponse(
        UUID id,
        @Schema(example = "BK-7KQ2M9XA") String bookingReference,
        BookingStatus bookingStatus,
        int numberOfSeats,
        @Schema(example = "1300.00") BigDecimal totalAmount,
        BookingRideInfo ride,
        UserSummary passenger,
        UserSummary driver,
        @Schema(description = "The other party's phone number; only shared once the booking is ACCEPTED")
        String contactPhone,
        Instant createdAt,
        Instant updatedAt) {
}
