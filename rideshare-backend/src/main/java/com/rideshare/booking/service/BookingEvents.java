package com.rideshare.booking.service;

import com.rideshare.booking.entity.Booking;
import com.rideshare.common.event.BookingEvent;
import com.rideshare.ride.entity.Ride;

/** Builds {@link BookingEvent}s from entities (must be called inside the transaction). */
public final class BookingEvents {

    private BookingEvents() {
    }

    public static BookingEvent of(BookingEvent.Type type, Booking booking) {
        Ride ride = booking.getRide();
        return new BookingEvent(type, booking.getId(), booking.getBookingReference(), ride.getId(),
                booking.getPassenger().getId(), booking.getPassenger().getFullName(), ride.getDriver().getId(),
                ride.route(), booking.getNumberOfSeats());
    }
}
