package com.rideshare.admin.dto;

import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;

import java.math.BigDecimal;
import java.util.Map;

public record PlatformStatsResponse(
        long totalUsers,
        long activeUsers,
        long deactivatedUsers,
        long totalVehicles,
        long pendingVehicleVerifications,
        long verifiedVehicles,
        long totalRides,
        Map<RideStatus, Long> ridesByStatus,
        long totalBookings,
        Map<BookingStatus, Long> bookingsByStatus,
        long totalReviews,
        BigDecimal totalSuccessfulPayments) {
}
