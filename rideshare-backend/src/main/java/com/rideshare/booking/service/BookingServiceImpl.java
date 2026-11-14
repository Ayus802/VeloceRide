package com.rideshare.booking.service;

import com.rideshare.booking.dto.BookingResponse;
import com.rideshare.booking.dto.CreateBookingRequest;
import com.rideshare.booking.entity.Booking;
import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.event.BookingEvent;
import com.rideshare.common.exception.BusinessRuleException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.common.util.TokenUtils;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.security.UserPrincipal;
import com.rideshare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Request-based booking workflow.
 *
 * <p><b>Concurrency:</b> every operation that reads or changes seat availability first takes a
 * {@code SELECT ... FOR UPDATE} lock on the ride row, then on the booking row (always in that order to avoid
 * deadlocks). Concurrent accepts on the same ride are therefore serialised and each one sees the seat count
 * left by the previous one, so the same seat can never be allocated twice. A CHECK constraint
 * ({@code available_seats >= 0}) is the final safety net.</p>
 */
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "bookingStatus", "numberOfSeats");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional
    public BookingResponse create(UUID passengerId, CreateBookingRequest request) {
        Ride ride = rideRepository.findByIdForUpdate(request.rideId())
                .orElseThrow(() -> ResourceNotFoundException.of("Ride"));
        if (ride.isDrivenBy(passengerId)) {
            throw new BusinessRuleException("You cannot book your own ride");
        }
        if (ride.getRideStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("This ride is no longer open for booking");
        }
        if (!ride.departureDateTime().isAfter(LocalDateTime.now(clock))) {
            throw new ConflictException("This ride has already departed");
        }
        if (request.numberOfSeats() > ride.getAvailableSeats()) {
            throw new BusinessRuleException("Only " + ride.getAvailableSeats() + " seat(s) available on this ride");
        }
        if (bookingRepository.existsByRideIdAndPassengerIdAndBookingStatusIn(ride.getId(), passengerId,
                BookingStatus.ACTIVE)) {
            throw new ConflictException("You already have an active booking for this ride");
        }
        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setPassenger(userRepository.getReferenceById(passengerId));
        booking.setNumberOfSeats(request.numberOfSeats());
        booking.setTotalAmount(ride.getPricePerSeat().multiply(BigDecimal.valueOf(request.numberOfSeats())));
        booking.setBookingReference(TokenUtils.reference("BK", 8));
        bookingRepository.saveAndFlush(booking);
        events.publishEvent(BookingEvents.of(BookingEvent.Type.REQUESTED, booking));
        return BookingMapper.toResponse(booking, passengerId);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(UserPrincipal caller, UUID bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        boolean participant = booking.isOwnedBy(caller.id()) || booking.getRide().isDrivenBy(caller.id());
        if (!participant && !caller.isAdmin()) {
            throw new ForbiddenException("You can only view bookings you made or received");
        }
        return BookingMapper.toResponse(booking, caller.id());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookings(UUID passengerId, BookingStatus status, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, SORTABLE, DEFAULT_SORT);
        Page<Booking> result = status == null
                ? bookingRepository.findByPassenger(passengerId, page)
                : bookingRepository.findByPassengerAndStatus(passengerId, status, page);
        return PageResponse.of(result, b -> BookingMapper.toResponse(b, passengerId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> receivedRequests(UUID driverId, UUID rideId, BookingStatus status,
                                                          Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, SORTABLE, DEFAULT_SORT);
        Page<Booking> result;
        if (rideId == null) {
            result = status == null
                    ? bookingRepository.findReceived(driverId, page)
                    : bookingRepository.findReceivedByStatus(driverId, status, page);
        } else {
            result = status == null
                    ? bookingRepository.findReceivedForRide(driverId, rideId, page)
                    : bookingRepository.findReceivedForRideAndStatus(driverId, rideId, status, page);
        }
        return PageResponse.of(result, b -> BookingMapper.toResponse(b, driverId));
    }

    @Override
    @Transactional
    public BookingResponse accept(UUID driverId, UUID bookingId) {
        Locked locked = lockRideThenBooking(bookingId);
        Ride ride = locked.ride();
        Booking booking = locked.booking();
        requireDriver(ride, driverId, "accept");
        requirePending(booking);
        if (ride.getRideStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Bookings can only be accepted while the ride is scheduled");
        }
        if (booking.getNumberOfSeats() > ride.getAvailableSeats()) {
            throw new BusinessRuleException("Not enough seats available: requested " + booking.getNumberOfSeats()
                    + ", available " + ride.getAvailableSeats());
        }
        ride.setAvailableSeats(ride.getAvailableSeats() - booking.getNumberOfSeats());
        booking.transitionTo(BookingStatus.ACCEPTED);
        rideRepository.saveAndFlush(ride);
        bookingRepository.saveAndFlush(booking);
        events.publishEvent(BookingEvents.of(BookingEvent.Type.ACCEPTED, booking));
        return BookingMapper.toResponse(booking, driverId);
    }

    @Override
    @Transactional
    public BookingResponse reject(UUID driverId, UUID bookingId) {
        Locked locked = lockRideThenBooking(bookingId);
        requireDriver(locked.ride(), driverId, "reject");
        Booking booking = locked.booking();
        requirePending(booking);
        booking.transitionTo(BookingStatus.REJECTED); // pending requests never held seats
        bookingRepository.saveAndFlush(booking);
        events.publishEvent(BookingEvents.of(BookingEvent.Type.REJECTED, booking));
        return BookingMapper.toResponse(booking, driverId);
    }

    @Override
    @Transactional
    public BookingResponse cancel(UUID passengerId, UUID bookingId) {
        Locked locked = lockRideThenBooking(bookingId);
        Ride ride = locked.ride();
        Booking booking = locked.booking();
        if (!booking.isOwnedBy(passengerId)) {
            throw new ForbiddenException("Only the passenger who made this booking can cancel it");
        }
        if (!BookingStatus.ACTIVE.contains(booking.getBookingStatus())) {
            throw new ConflictException("Booking is already " + booking.getBookingStatus());
        }
        if (ride.getRideStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Bookings cannot be cancelled once the ride has started");
        }
        if (booking.getBookingStatus() == BookingStatus.ACCEPTED) {
            ride.setAvailableSeats(ride.getAvailableSeats() + booking.getNumberOfSeats()); // release the seats
            rideRepository.saveAndFlush(ride);
        }
        booking.transitionTo(BookingStatus.CANCELLED);
        bookingRepository.saveAndFlush(booking);
        events.publishEvent(BookingEvents.of(BookingEvent.Type.CANCELLED_BY_PASSENGER, booking));
        return BookingMapper.toResponse(booking, passengerId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listAll(BookingStatus status, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, SORTABLE, DEFAULT_SORT);
        Page<Booking> result = status == null
                ? bookingRepository.findAllDetailed(page)
                : bookingRepository.findAllDetailedByStatus(status, page);
        return PageResponse.of(result, b -> BookingMapper.toResponse(b, null));
    }

    private record Locked(Ride ride, Booking booking) {
    }

    /** Lock order: ride row first, then booking row. Both are read fresh after the lock is granted. */
    private Locked lockRideThenBooking(UUID bookingId) {
        UUID rideId = bookingRepository.findRideIdById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        Ride ride = rideRepository.findByIdForUpdate(rideId).orElseThrow(() -> ResourceNotFoundException.of("Ride"));
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        return new Locked(ride, booking);
    }

    private static void requireDriver(Ride ride, UUID userId, String action) {
        if (!ride.isDrivenBy(userId)) {
            throw new ForbiddenException("Only the driver of this ride can " + action + " booking requests");
        }
    }

    private static void requirePending(Booking booking) {
        if (booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Booking is already " + booking.getBookingStatus());
        }
    }
}
