package com.rideshare.booking;

import com.rideshare.booking.dto.BookingResponse;
import com.rideshare.booking.dto.CreateBookingRequest;
import com.rideshare.booking.entity.Booking;
import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.booking.service.BookingServiceImpl;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.event.BookingEvent;
import com.rideshare.common.exception.BusinessRuleException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.support.Fixtures;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), ZONE);

    @Mock
    BookingRepository bookingRepository;
    @Mock
    RideRepository rideRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ApplicationEventPublisher events;

    BookingServiceImpl service;
    User driver;
    User passenger;
    Ride ride;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(bookingRepository, rideRepository, userRepository, events, CLOCK);
        driver = Fixtures.user("Driver");
        passenger = Fixtures.user("Passenger");
        ride = Fixtures.ride(driver, 3, 3, LocalDate.of(2026, 11, 15));
    }

    @Test
    void createBookingIsPendingAndPublishesRequestEvent() {
        when(rideRepository.findByIdForUpdate(ride.getId())).thenReturn(Optional.of(ride));
        when(userRepository.getReferenceById(passenger.getId())).thenReturn(passenger);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.create(passenger.getId(), new CreateBookingRequest(ride.getId(), 2));

        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(ride.getAvailableSeats()).isEqualTo(3); // pending requests hold no seats
        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(BookingEvent.Type.REQUESTED);
        assertThat(event.getValue().driverId()).isEqualTo(driver.getId());
    }

    @Test
    void cannotBookOwnRide() {
        when(rideRepository.findByIdForUpdate(ride.getId())).thenReturn(Optional.of(ride));
        assertThatThrownBy(() -> service.create(driver.getId(), new CreateBookingRequest(ride.getId(), 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You cannot book your own ride");
    }

    @Test
    void cannotRequestMoreSeatsThanAvailable() {
        ride.setAvailableSeats(1);
        when(rideRepository.findByIdForUpdate(ride.getId())).thenReturn(Optional.of(ride));
        assertThatThrownBy(() -> service.create(passenger.getId(), new CreateBookingRequest(ride.getId(), 2)))
                .isInstanceOf(BusinessRuleException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateActiveBookingIsRejected() {
        when(rideRepository.findByIdForUpdate(ride.getId())).thenReturn(Optional.of(ride));
        when(bookingRepository.existsByRideIdAndPassengerIdAndBookingStatusIn(any(), any(), anyCollection()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.create(passenger.getId(), new CreateBookingRequest(ride.getId(), 1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptAllocatesSeats() {
        Booking booking = Fixtures.booking(ride, passenger, 2);
        stubLocks(booking);
        service.accept(driver.getId(), booking.getId());
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.ACCEPTED);
        assertThat(ride.getAvailableSeats()).isEqualTo(1);
    }

    @Test
    void acceptRejectedWhenNotEnoughSeats() {
        ride.setAvailableSeats(1);
        Booking booking = Fixtures.booking(ride, passenger, 2);
        stubLocks(booking);
        assertThatThrownBy(() -> service.accept(driver.getId(), booking.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(ride.getAvailableSeats()).isEqualTo(1);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void onlyDriverCanAccept() {
        Booking booking = Fixtures.booking(ride, passenger, 1);
        stubLocks(booking);
        assertThatThrownBy(() -> service.accept(passenger.getId(), booking.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancellingAcceptedBookingReleasesSeats() {
        Booking booking = Fixtures.booking(ride, passenger, 2);
        booking.transitionTo(BookingStatus.ACCEPTED);
        ride.setAvailableSeats(1);
        stubLocks(booking);
        service.cancel(passenger.getId(), booking.getId());
        assertThat(ride.getAvailableSeats()).isEqualTo(3);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void rejectingNonPendingBookingIsAConflict() {
        Booking booking = Fixtures.booking(ride, passenger, 1);
        booking.transitionTo(BookingStatus.ACCEPTED);
        stubLocks(booking);
        assertThatThrownBy(() -> service.reject(driver.getId(), booking.getId()))
                .isInstanceOf(ConflictException.class);
    }

    private void stubLocks(Booking booking) {
        when(bookingRepository.findRideIdById(booking.getId())).thenReturn(Optional.of(ride.getId()));
        when(rideRepository.findByIdForUpdate(ride.getId())).thenReturn(Optional.of(ride));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
    }
}
