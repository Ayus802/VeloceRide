package com.rideshare.ride.service;

import com.rideshare.booking.entity.Booking;
import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.booking.service.BookingEvents;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.event.BookingEvent;
import com.rideshare.common.event.RideEvent;
import com.rideshare.common.exception.BadRequestException;
import com.rideshare.common.exception.BusinessRuleException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.config.AppProperties;
import com.rideshare.ride.dto.RideRequest;
import com.rideshare.ride.dto.RideResponse;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.user.repository.UserRepository;
import com.rideshare.vehicle.entity.Vehicle;
import com.rideshare.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RideServiceImpl implements RideService {

    private static final Set<String> SEARCH_SORT = Set.of("departureTime", "pricePerSeat", "availableSeats", "createdAt");
    private static final Set<String> LIST_SORT = Set.of("departureDate", "departureTime", "pricePerSeat",
            "availableSeats", "createdAt", "rideStatus");

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleService vehicleService;
    private final RideMapper rideMapper;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------------ publish / read / update

    @Override
    @Transactional
    public RideResponse publish(UUID driverId, RideRequest request) {
        Vehicle vehicle = vehicleService.requireUsableVehicle(driverId, request.vehicleId());
        validate(request, vehicle);
        Ride ride = new Ride();
        ride.setDriver(userRepository.getReferenceById(driverId)); // the caller is always the driver
        ride.setVehicle(vehicle);
        apply(ride, request);
        ride.setAvailableSeats(request.totalSeats());
        ride.setRideStatus(RideStatus.SCHEDULED);
        return rideMapper.toResponse(rideRepository.saveAndFlush(ride));
    }

    @Override
    @Transactional(readOnly = true)
    public RideResponse getRide(UUID rideId) {
        return rideMapper.toResponse(rideRepository.findDetailedById(rideId)
                .orElseThrow(() -> ResourceNotFoundException.of("Ride")));
    }

    @Override
    @Transactional
    public RideResponse update(UUID driverId, UUID rideId, RideRequest request) {
        Ride ride = lockOwnedRide(driverId, rideId, "modify");
        if (ride.getRideStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Only scheduled rides can be updated (current status: " + ride.getRideStatus() + ")");
        }
        Vehicle vehicle = vehicleService.requireUsableVehicle(driverId, request.vehicleId());
        validate(request, vehicle);

        boolean hasActiveBookings = bookingRepository.existsByRideIdAndBookingStatusIn(rideId, BookingStatus.ACTIVE);
        if (hasActiveBookings && changesCommittedTerms(ride, request)) {
            throw new ConflictException("Route, schedule, price and vehicle cannot change once passengers have "
                    + "requested or booked seats; cancel the ride instead");
        }
        int bookedSeats = ride.bookedSeats();
        if (request.totalSeats() < bookedSeats) {
            throw new BusinessRuleException("Total seats cannot be less than the " + bookedSeats + " seat(s) already booked");
        }
        ride.setVehicle(vehicle);
        apply(ride, request);
        ride.setAvailableSeats(request.totalSeats() - bookedSeats);
        return rideMapper.toResponse(rideRepository.saveAndFlush(ride));
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    @Transactional
    public RideResponse cancel(UUID driverId, UUID rideId) {
        Ride ride = lockOwnedRide(driverId, rideId, "cancel");
        requireTransition(ride, RideStatus.CANCELLED);

        List<Booking> active = bookingRepository.findByRideAndStatuses(rideId, BookingStatus.ACTIVE);
        List<UUID> affectedPassengers = active.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.ACCEPTED)
                .map(b -> b.getPassenger().getId())
                .toList();
        for (Booking booking : active) {
            booking.transitionTo(BookingStatus.CANCELLED);
            events.publishEvent(BookingEvents.of(BookingEvent.Type.CANCELLED_BY_RIDE, booking));
        }
        ride.setRideStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(clock.instant());
        ride.setAvailableSeats(ride.getTotalSeats());
        events.publishEvent(new RideEvent(RideEvent.Type.CANCELLED, rideId, driverId, ride.route(), affectedPassengers));
        return rideMapper.toResponse(rideRepository.saveAndFlush(ride));
    }

    @Override
    @Transactional
    public RideResponse start(UUID driverId, UUID rideId) {
        Ride ride = lockOwnedRide(driverId, rideId, "start");
        requireTransition(ride, RideStatus.ONGOING);
        Duration window = properties.ride().startAllowedBeforeDeparture();
        if (LocalDateTime.now(clock).isBefore(ride.departureDateTime().minus(window))) {
            throw new BusinessRuleException("A ride can be started at most " + humanize(window) + " before departure");
        }
        // Requests that were never answered can no longer be accepted.
        for (Booking pending : bookingRepository.findByRideAndStatuses(rideId, List.of(BookingStatus.PENDING))) {
            pending.transitionTo(BookingStatus.REJECTED);
            events.publishEvent(BookingEvents.of(BookingEvent.Type.AUTO_REJECTED, pending));
        }
        List<UUID> passengers = bookingRepository.findByRideAndStatuses(rideId, List.of(BookingStatus.ACCEPTED))
                .stream().map(b -> b.getPassenger().getId()).toList();
        ride.setRideStatus(RideStatus.ONGOING);
        ride.setStartedAt(clock.instant());
        events.publishEvent(new RideEvent(RideEvent.Type.STARTED, rideId, driverId, ride.route(), passengers));
        return rideMapper.toResponse(rideRepository.saveAndFlush(ride));
    }

    @Override
    @Transactional
    public RideResponse complete(UUID driverId, UUID rideId) {
        Ride ride = lockOwnedRide(driverId, rideId, "complete");
        requireTransition(ride, RideStatus.COMPLETED);
        List<Booking> accepted = bookingRepository.findByRideAndStatuses(rideId, List.of(BookingStatus.ACCEPTED));
        for (Booking booking : accepted) {
            booking.transitionTo(BookingStatus.COMPLETED);
            events.publishEvent(BookingEvents.of(BookingEvent.Type.COMPLETED, booking));
        }
        ride.setRideStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(clock.instant());
        List<UUID> passengers = accepted.stream().map(b -> b.getPassenger().getId()).toList();
        events.publishEvent(new RideEvent(RideEvent.Type.COMPLETED, rideId, driverId, ride.route(), passengers));
        return rideMapper.toResponse(rideRepository.saveAndFlush(ride));
    }

    // ------------------------------------------------------------------ listing / search

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RideResponse> getMyRides(UUID driverId, RideStatus status, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, LIST_SORT,
                Sort.by(Sort.Direction.DESC, "departureDate", "departureTime"));
        Page<Ride> rides = status == null
                ? rideRepository.findByDriver(driverId, page)
                : rideRepository.findByDriverAndStatus(driverId, status, page);
        return PageResponse.of(rides, rideMapper.toResponses(rides.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RideResponse> listUpcoming(Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, LIST_SORT,
                Sort.by(Sort.Direction.ASC, "departureDate", "departureTime"));
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Ride> rides = rideRepository.findUpcoming(RideStatus.SCHEDULED, now.toLocalDate(), now.toLocalTime(), page);
        return PageResponse.of(rides, rideMapper.toResponses(rides.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RideResponse> search(String source, String destination, LocalDate date, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (date.isBefore(now.toLocalDate())) {
            throw new BadRequestException("Journey date cannot be in the past");
        }
        Pageable page = PageableUtils.sanitize(pageable, SEARCH_SORT, Sort.by(Sort.Direction.ASC, "departureTime"));
        Page<Ride> rides = rideRepository.search(RideStatus.SCHEDULED, likePattern(source), likePattern(destination),
                date, now.toLocalDate(), now.toLocalTime(), page);
        return PageResponse.of(rides, rideMapper.toResponses(rides.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RideResponse> listAll(RideStatus status, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, LIST_SORT, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ride> rides = status == null
                ? rideRepository.findAllDetailed(page)
                : rideRepository.findAllDetailedByStatus(status, page);
        return PageResponse.of(rides, rideMapper.toResponses(rides.getContent()));
    }

    // ------------------------------------------------------------------ helpers

    private Ride lockOwnedRide(UUID driverId, UUID rideId, String action) {
        Ride ride = rideRepository.findByIdForUpdate(rideId).orElseThrow(() -> ResourceNotFoundException.of("Ride"));
        if (!ride.isDrivenBy(driverId)) {
            throw new ForbiddenException("Only the driver who published this ride can " + action + " it");
        }
        return ride;
    }

    private static void requireTransition(Ride ride, RideStatus target) {
        if (!ride.getRideStatus().canTransitionTo(target)) {
            throw new ConflictException("Ride cannot move from " + ride.getRideStatus() + " to " + target);
        }
    }

    private void validate(RideRequest request, Vehicle vehicle) {
        if (request.source().trim().equalsIgnoreCase(request.destination().trim())) {
            throw new BadRequestException("Source and destination must be different");
        }
        LocalDateTime departure = LocalDateTime.of(request.departureDate(), request.departureTime());
        if (!departure.isAfter(LocalDateTime.now(clock))) {
            throw new BadRequestException("Departure date and time must be in the future");
        }
        if (request.estimatedArrivalTime() != null && !request.estimatedArrivalTime().isAfter(departure)) {
            throw new BadRequestException("Estimated arrival time must be after departure");
        }
        if (request.totalSeats() > vehicle.getSeatingCapacity()) {
            throw new BusinessRuleException("Total seats cannot exceed the vehicle's seating capacity of "
                    + vehicle.getSeatingCapacity());
        }
    }

    private static boolean changesCommittedTerms(Ride ride, RideRequest request) {
        return !ride.getSource().equalsIgnoreCase(request.source().trim())
                || !ride.getDestination().equalsIgnoreCase(request.destination().trim())
                || !ride.getDepartureDate().equals(request.departureDate())
                || !ride.getDepartureTime().equals(request.departureTime())
                || ride.getPricePerSeat().compareTo(request.pricePerSeat()) != 0
                || !Objects.equals(ride.getVehicle().getId(), request.vehicleId());
    }

    private static void apply(Ride ride, RideRequest r) {
        ride.setSource(r.source().trim());
        ride.setDestination(r.destination().trim());
        ride.setSourceLatitude(r.sourceLatitude());
        ride.setSourceLongitude(r.sourceLongitude());
        ride.setDestinationLatitude(r.destinationLatitude());
        ride.setDestinationLongitude(r.destinationLongitude());
        ride.setDepartureDate(r.departureDate());
        ride.setDepartureTime(r.departureTime().withNano(0));
        ride.setEstimatedArrivalTime(r.estimatedArrivalTime());
        ride.setTotalSeats(r.totalSeats());
        ride.setPricePerSeat(r.pricePerSeat());
        ride.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
    }

    /** Case-insensitive "contains" pattern with LIKE wildcards in user input escaped. */
    static String likePattern(String input) {
        String escaped = input.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String humanize(Duration d) {
        if (d.toDays() > 0 && d.toHoursPart() == 0) {
            return d.toDays() + " day(s)";
        }
        if (d.toHours() > 0 && d.toMinutesPart() == 0) {
            return d.toHours() + " hour(s)";
        }
        return d.toMinutes() + " minute(s)";
    }
}
