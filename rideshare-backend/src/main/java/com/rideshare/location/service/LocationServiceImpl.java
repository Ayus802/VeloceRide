package com.rideshare.location.service;

import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.exception.BadRequestException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.exception.TooManyRequestsException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.AfterCommit;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.config.AppProperties;
import com.rideshare.location.dto.LocationResponse;
import com.rideshare.location.dto.LocationUpdateRequest;
import com.rideshare.location.dto.TrackingStatusMessage;
import com.rideshare.location.entity.RideLocation;
import com.rideshare.location.repository.RideLocationRepository;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live driver location.
 * <ul>
 *   <li>Publish: only the ride's own driver, only while the ride is ONGOING, coordinates validated,
 *       rate-limited per ride ({@code app.location.min-update-interval}).</li>
 *   <li>View/subscribe: the driver or a passenger holding an ACCEPTED booking on that ride.</li>
 *   <li>Nothing is tracked when no ride is ongoing; state is cleared when the ride completes or is cancelled.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    public static final String LOCATION_TOPIC = "/topic/rides/%s/location";
    public static final String STATUS_TOPIC = "/topic/rides/%s/status";

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RideLocationRepository locationRepository;
    private final LatestLocationStore latestStore;
    private final SimpMessagingTemplate messagingTemplate;
    private final AppProperties properties;
    private final Clock clock;

    private final Map<UUID, Instant> lastAcceptedUpdate = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public LocationResponse publishUpdate(UUID driverId, LocationUpdateRequest request) {
        validateCoordinates(request.latitude(), request.longitude());
        if (request.rideId() == null) {
            throw new BadRequestException("rideId is required");
        }
        Ride ride = rideRepository.findById(request.rideId()).orElseThrow(() -> ResourceNotFoundException.of("Ride"));
        if (!ride.isDrivenBy(driverId)) {
            // Prevents a driver from spoofing another ride by changing the rideId in the payload.
            throw new ForbiddenException("Only the assigned driver can publish location for this ride");
        }
        if (ride.getRideStatus() != RideStatus.ONGOING) {
            throw new ConflictException("Location tracking is only active while the ride is ongoing");
        }

        Instant now = clock.instant();
        Instant recordedAt = resolveTimestamp(request, now);
        enforceRateLimit(ride.getId(), now);

        LocationResponse location = new LocationResponse(ride.getId(), request.latitude(), request.longitude(),
                recordedAt, now);
        if (properties.location().historyEnabled()) {
            RideLocation entity = new RideLocation();
            entity.setRide(ride);
            entity.setDriver(userRepository.getReferenceById(driverId));
            entity.setLatitude(request.latitude());
            entity.setLongitude(request.longitude());
            entity.setRecordedAt(recordedAt);
            locationRepository.save(entity);
        }
        AfterCommit.run(() -> {
            latestStore.put(location);
            messagingTemplate.convertAndSend(LOCATION_TOPIC.formatted(ride.getId()), location);
        });
        return location;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canSubscribe(UUID userId, UUID rideId) {
        return rideRepository.findById(rideId)
                .filter(r -> r.getRideStatus() == RideStatus.SCHEDULED || r.getRideStatus() == RideStatus.ONGOING)
                .map(r -> r.isDrivenBy(userId) || bookingRepository.existsByRideIdAndPassengerIdAndBookingStatus(
                        rideId, userId, BookingStatus.ACCEPTED))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationResponse getLatest(UUID userId, UUID rideId) {
        Ride ride = rideRepository.findById(rideId).orElseThrow(() -> ResourceNotFoundException.of("Ride"));
        if (!ride.isDrivenBy(userId) && !bookingRepository.existsByRideIdAndPassengerIdAndBookingStatus(
                rideId, userId, BookingStatus.ACCEPTED)) {
            throw new ForbiddenException("Only the driver and accepted passengers can view this ride's location");
        }
        if (ride.getRideStatus() != RideStatus.ONGOING) {
            throw new ConflictException("Location tracking is not active for this ride");
        }
        return latestStore.get(rideId)
                .or(() -> locationRepository.findFirstByRideIdOrderByRecordedAtDesc(rideId).map(this::toResponse))
                .orElseThrow(() -> new ResourceNotFoundException("No location has been shared for this ride yet"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> getHistory(UUID userId, UUID rideId, Pageable pageable) {
        Ride ride = rideRepository.findById(rideId).orElseThrow(() -> ResourceNotFoundException.of("Ride"));
        boolean allowed = ride.isDrivenBy(userId) || bookingRepository.existsByRideIdAndPassengerIdAndBookingStatusIn(
                rideId, userId, Set.of(BookingStatus.ACCEPTED, BookingStatus.COMPLETED));
        if (!allowed) {
            throw new ForbiddenException("Only the driver and accepted passengers can view this ride's location history");
        }
        if (!properties.location().historyEnabled()) {
            throw new ResourceNotFoundException("Location history is disabled");
        }
        Pageable page = PageableUtils.sanitize(pageable, Set.of("recordedAt"), Sort.by(Sort.Direction.DESC, "recordedAt"));
        return PageResponse.of(locationRepository.findByRideId(rideId, page), this::toResponse);
    }

    @Override
    public void onRideStatusChanged(UUID rideId, RideStatus status) {
        boolean active = status == RideStatus.ONGOING;
        AfterCommit.run(() -> {
            if (!active) {
                latestStore.remove(rideId);
                lastAcceptedUpdate.remove(rideId);
            }
            messagingTemplate.convertAndSend(STATUS_TOPIC.formatted(rideId),
                    new TrackingStatusMessage(rideId, status, active));
        });
    }

    private void enforceRateLimit(UUID rideId, Instant now) {
        Duration minInterval = properties.location().minUpdateInterval();
        AtomicBoolean accepted = new AtomicBoolean(false);
        lastAcceptedUpdate.compute(rideId, (id, previous) -> {
            if (previous == null || !now.isBefore(previous.plus(minInterval))) {
                accepted.set(true);
                return now;
            }
            return previous;
        });
        if (!accepted.get()) {
            throw new TooManyRequestsException("Location updates are limited to one every "
                    + minInterval.toMillis() + " ms");
        }
    }

    private Instant resolveTimestamp(LocationUpdateRequest request, Instant now) {
        if (request.timestamp() == null) {
            return now;
        }
        Instant ts = request.timestamp().atZone(clock.getZone()).toInstant();
        Duration skew = properties.location().maxClientClockSkew();
        if (ts.isAfter(now.plus(skew)) || ts.isBefore(now.minus(skew))) {
            throw new BadRequestException("Location timestamp is too far from server time");
        }
        return ts;
    }

    static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null || latitude.isNaN() || longitude.isNaN()
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BadRequestException("Invalid coordinates: latitude must be within [-90, 90] and "
                    + "longitude within [-180, 180]");
        }
    }

    private LocationResponse toResponse(RideLocation l) {
        return new LocationResponse(l.getRide().getId(), l.getLatitude(), l.getLongitude(), l.getRecordedAt(),
                l.getCreatedAt());
    }
}
