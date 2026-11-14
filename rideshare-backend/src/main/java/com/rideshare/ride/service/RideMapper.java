package com.rideshare.ride.service;

import com.rideshare.review.dto.UserRatingResponse;
import com.rideshare.review.service.RatingService;
import com.rideshare.ride.dto.DriverSummary;
import com.rideshare.ride.dto.RideResponse;
import com.rideshare.ride.entity.Ride;
import com.rideshare.user.entity.User;
import com.rideshare.vehicle.dto.VehicleSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity -> DTO mapping. Driver ratings for a whole page are loaded with a single aggregate query.
 */
@Component
@RequiredArgsConstructor
public class RideMapper {

    private final RatingService ratingService;

    public RideResponse toResponse(Ride ride) {
        return toResponse(ride, ratingService.getRating(ride.getDriver().getId()));
    }

    public List<RideResponse> toResponses(List<Ride> rides) {
        List<UUID> driverIds = rides.stream().map(r -> r.getDriver().getId()).distinct().toList();
        Map<UUID, UserRatingResponse> ratings = ratingService.getRatings(driverIds);
        return rides.stream()
                .map(r -> toResponse(r, ratings.getOrDefault(r.getDriver().getId(),
                        UserRatingResponse.empty(r.getDriver().getId()))))
                .toList();
    }

    private RideResponse toResponse(Ride r, UserRatingResponse rating) {
        User d = r.getDriver();
        DriverSummary driver = new DriverSummary(d.getId(), d.getFullName(), d.getProfilePicture(),
                rating.averageRating(), rating.totalReviews());
        return new RideResponse(r.getId(), r.getSource(), r.getDestination(), r.getSourceLatitude(),
                r.getSourceLongitude(), r.getDestinationLatitude(), r.getDestinationLongitude(), r.getDepartureDate(),
                r.getDepartureTime(), r.getEstimatedArrivalTime(), r.getTotalSeats(), r.getAvailableSeats(),
                r.bookedSeats(), r.getPricePerSeat(), r.getDescription(), r.getRideStatus(), driver,
                VehicleSummary.from(r.getVehicle()), r.getCreatedAt(), r.getUpdatedAt());
    }
}
