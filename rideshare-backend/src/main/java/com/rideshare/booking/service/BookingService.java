package com.rideshare.booking.service;

import com.rideshare.booking.dto.BookingResponse;
import com.rideshare.booking.dto.CreateBookingRequest;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.response.PageResponse;
import com.rideshare.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingService {

    BookingResponse create(UUID passengerId, CreateBookingRequest request);

    BookingResponse get(UserPrincipal caller, UUID bookingId);

    PageResponse<BookingResponse> myBookings(UUID passengerId, BookingStatus status, Pageable pageable);

    PageResponse<BookingResponse> receivedRequests(UUID driverId, UUID rideId, BookingStatus status, Pageable pageable);

    BookingResponse accept(UUID driverId, UUID bookingId);

    BookingResponse reject(UUID driverId, UUID bookingId);

    BookingResponse cancel(UUID passengerId, UUID bookingId);

    /** Admin listing. */
    PageResponse<BookingResponse> listAll(BookingStatus status, Pageable pageable);
}
