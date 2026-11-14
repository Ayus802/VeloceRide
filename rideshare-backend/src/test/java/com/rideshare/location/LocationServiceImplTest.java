package com.rideshare.location;

import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.exception.BadRequestException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.common.exception.TooManyRequestsException;
import com.rideshare.config.AppProperties;
import com.rideshare.location.dto.LocationUpdateRequest;
import com.rideshare.location.repository.RideLocationRepository;
import com.rideshare.location.service.InMemoryLatestLocationStore;
import com.rideshare.location.service.LocationServiceImpl;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.support.Fixtures;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationServiceImplTest {

    @Mock
    RideRepository rideRepository;
    @Mock
    BookingRepository bookingRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    RideLocationRepository locationRepository;
    @Mock
    SimpMessagingTemplate messagingTemplate;

    LocationServiceImpl service;
    User driver;
    Ride ride;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("UTC", null, null, null, null, null,
                new AppProperties.Location(Duration.ofSeconds(5), false, Duration.ofMinutes(5)), null, null, null, null, null);
        service = new LocationServiceImpl(rideRepository, bookingRepository, userRepository, locationRepository,
                new InMemoryLatestLocationStore(), messagingTemplate, props, Clock.systemUTC());
        driver = Fixtures.user("Driver");
        ride = Fixtures.ride(driver, 3, 3, LocalDate.now());
        ride.setRideStatus(RideStatus.ONGOING);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
    }

    private LocationUpdateRequest update(double lat, double lng) {
        return new LocationUpdateRequest(ride.getId(), lat, lng, null);
    }

    @Test
    void driverUpdateIsBroadcastToRideTopic() {
        service.publishUpdate(driver.getId(), update(28.61, 77.20));
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThatThrownBy(() -> service.publishUpdate(driver.getId(), update(95, 10))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.publishUpdate(driver.getId(), update(10, 181))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.publishUpdate(driver.getId(), update(Double.NaN, 10))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void onlyAssignedDriverMayPublish() {
        assertThatThrownBy(() -> service.publishUpdate(Fixtures.user("Other").getId(), update(1, 1)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void noTrackingWhenRideNotOngoing() {
        ride.setRideStatus(RideStatus.COMPLETED);
        assertThatThrownBy(() -> service.publishUpdate(driver.getId(), update(1, 1))).isInstanceOf(ConflictException.class);
    }

    @Test
    void updatesAreRateLimitedPerRide() {
        service.publishUpdate(driver.getId(), update(1, 1));
        assertThatThrownBy(() -> service.publishUpdate(driver.getId(), update(1.1, 1.1)))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void clientTimestampFarFromServerTimeIsRejected() {
        LocationUpdateRequest stale = new LocationUpdateRequest(ride.getId(), 1.0, 1.0,
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2));
        assertThatThrownBy(() -> service.publishUpdate(driver.getId(), stale)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void subscriptionAllowedOnlyForDriverAndAcceptedPassengers() {
        User passenger = Fixtures.user("Passenger");
        User stranger = Fixtures.user("Stranger");
        when(bookingRepository.existsByRideIdAndPassengerIdAndBookingStatus(ride.getId(), passenger.getId(),
                BookingStatus.ACCEPTED)).thenReturn(true);
        assertThat(service.canSubscribe(driver.getId(), ride.getId())).isTrue();
        assertThat(service.canSubscribe(passenger.getId(), ride.getId())).isTrue();
        assertThat(service.canSubscribe(stranger.getId(), ride.getId())).isFalse();
        ride.setRideStatus(RideStatus.COMPLETED);
        assertThat(service.canSubscribe(passenger.getId(), ride.getId())).isFalse();
    }
}
