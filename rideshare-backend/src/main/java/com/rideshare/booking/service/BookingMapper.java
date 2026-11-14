package com.rideshare.booking.service;

import com.rideshare.booking.dto.BookingResponse;
import com.rideshare.booking.dto.BookingRideInfo;
import com.rideshare.booking.entity.Booking;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.ride.entity.Ride;
import com.rideshare.user.dto.UserSummary;
import com.rideshare.user.entity.User;

import java.util.UUID;

/** Maps bookings for a specific viewer so contact details are only revealed to the right party. */
public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingResponse toResponse(Booking b, UUID viewerId) {
        Ride ride = b.getRide();
        User driver = ride.getDriver();
        User passenger = b.getPassenger();
        String contactPhone = null;
        if (b.getBookingStatus() == BookingStatus.ACCEPTED) {
            if (passenger.getId().equals(viewerId)) {
                contactPhone = driver.getPhoneNumber();
            } else if (driver.getId().equals(viewerId)) {
                contactPhone = passenger.getPhoneNumber();
            }
        }
        BookingRideInfo rideInfo = new BookingRideInfo(ride.getId(), ride.getSource(), ride.getDestination(),
                ride.getDepartureDate(), ride.getDepartureTime(), ride.getRideStatus(), ride.getPricePerSeat());
        return new BookingResponse(b.getId(), b.getBookingReference(), b.getBookingStatus(), b.getNumberOfSeats(),
                b.getTotalAmount(), rideInfo, UserSummary.from(passenger), UserSummary.from(driver), contactPhone,
                b.getCreatedAt(), b.getUpdatedAt());
    }
}
