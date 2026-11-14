package com.rideshare.booking;

import com.rideshare.booking.service.BookingService;
import com.rideshare.common.exception.ApiException;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    RideRepository rideRepository;

    @Test
    void createBookingIsPendingAndDoesNotConsumeSeats() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        doPost("/api/bookings", passenger, Map.of("rideId", f.rideId(), "numberOfSeats", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bookingStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(1000.00))
                .andExpect(jsonPath("$.data.passenger.id").value(passenger.id().toString()))
                .andExpect(jsonPath("$.data.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.data.bookingReference").isNotEmpty());
        doGet("/api/rides/" + f.rideId(), null).andExpect(jsonPath("$.data.availableSeats").value(3));
    }

    @Test
    void rejectsInvalidBookings() throws Exception {
        RideFixture f = rideFixture(2);
        TestUser passenger = registerUser();
        doPost("/api/bookings", passenger, Map.of("rideId", f.rideId(), "numberOfSeats", 3))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only 2 seat(s) available on this ride"));
        doPost("/api/bookings", passenger, Map.of("rideId", f.rideId(), "numberOfSeats", 0))
                .andExpect(status().isBadRequest());
        doPost("/api/bookings", f.driver(), Map.of("rideId", f.rideId(), "numberOfSeats", 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You cannot book your own ride"));
        doPost("/api/bookings", passenger, Map.of("rideId", UUID.randomUUID(), "numberOfSeats", 1))
                .andExpect(status().isNotFound());

        requestBooking(passenger, f.rideId(), 1);
        doPost("/api/bookings", passenger, Map.of("rideId", f.rideId(), "numberOfSeats", 1))
                .andExpect(status().isConflict());
    }

    @Test
    void driverAcceptsAndRejectsRequests() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser p1 = registerUser();
        TestUser p2 = registerUser();
        UUID b1 = requestBooking(p1, f.rideId(), 2);
        UUID b2 = requestBooking(p2, f.rideId(), 1);

        doGet("/api/bookings/received-requests?status=PENDING&rideId=" + f.rideId(), f.driver())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // Only the ride's driver can decide.
        doPatch("/api/bookings/" + b1 + "/accept", p2, null).andExpect(status().isForbidden());

        doPatch("/api/bookings/" + b1 + "/accept", f.driver(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.contactPhone").isNotEmpty());
        doGet("/api/rides/" + f.rideId(), null).andExpect(jsonPath("$.data.availableSeats").value(1));

        doPatch("/api/bookings/" + b2 + "/reject", f.driver(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingStatus").value("REJECTED"));
        doGet("/api/rides/" + f.rideId(), null).andExpect(jsonPath("$.data.availableSeats").value(1));

        // Invalid transitions.
        doPatch("/api/bookings/" + b1 + "/accept", f.driver(), null).andExpect(status().isConflict());
        doPatch("/api/bookings/" + b2 + "/accept", f.driver(), null).andExpect(status().isConflict());
        doPatch("/api/bookings/" + b1 + "/reject", f.driver(), null).andExpect(status().isConflict());

        doGet("/api/bookings/my-bookings", p1)
                .andExpect(jsonPath("$.data.content[0].id").value(b1.toString()));
    }

    @Test
    void acceptFailsWhenSeatsRunOut() throws Exception {
        RideFixture f = rideFixture(2);
        UUID b1 = requestBooking(registerUser(), f.rideId(), 2);
        UUID b2 = requestBooking(registerUser(), f.rideId(), 1);
        accept(f.driver(), b1);
        doPatch("/api/bookings/" + b2 + "/accept", f.driver(), null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void passengerCancelsAndSeatsAreReleased() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        UUID booking = requestBooking(passenger, f.rideId(), 2);
        accept(f.driver(), booking);
        doGet("/api/rides/" + f.rideId(), null).andExpect(jsonPath("$.data.availableSeats").value(1));

        doPatch("/api/bookings/" + booking + "/cancel", f.driver(), null).andExpect(status().isForbidden());
        doPatch("/api/bookings/" + booking + "/cancel", passenger, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingStatus").value("CANCELLED"));
        doGet("/api/rides/" + f.rideId(), null).andExpect(jsonPath("$.data.availableSeats").value(3));
        doPatch("/api/bookings/" + booking + "/cancel", passenger, null).andExpect(status().isConflict());

        // A new booking is possible after cancelling.
        requestBooking(passenger, f.rideId(), 1);
    }

    @Test
    void cannotCancelAfterRideStarted() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        UUID booking = requestBooking(passenger, f.rideId(), 1);
        accept(f.driver(), booking);
        startRide(f.driver(), f.rideId());
        doPatch("/api/bookings/" + booking + "/cancel", passenger, null).andExpect(status().isConflict());
    }

    @Test
    void outsidersCannotViewBookings() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        UUID booking = requestBooking(passenger, f.rideId(), 1);
        doGet("/api/bookings/" + booking, passenger).andExpect(status().isOk());
        doGet("/api/bookings/" + booking, f.driver()).andExpect(status().isOk());
        doGet("/api/bookings/" + booking, registerUser()).andExpect(status().isForbidden());
        doGet("/api/bookings/" + booking, admin()).andExpect(status().isOk());
    }

    @Test
    void concurrentAcceptsNeverOverbook() throws Exception {
        int seats = 3;
        int requests = 8;
        RideFixture f = rideFixture(seats);
        List<UUID> bookings = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            bookings.add(requestBooking(registerUser(), f.rideId(), 1));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UUID bookingId : bookings) {
            Callable<Boolean> task = () -> {
                startGate.await();
                try {
                    bookingService.accept(f.driver().id(), bookingId);
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        startGate.countDown();
        int accepted = 0;
        for (Future<Boolean> r : results) {
            if (r.get(30, TimeUnit.SECONDS)) {
                accepted++;
            }
        }
        pool.shutdown();

        assertThat(accepted).isEqualTo(seats);
        assertThat(rideRepository.findById(f.rideId()).orElseThrow().getAvailableSeats()).isZero();
        doGet("/api/bookings/received-requests?status=ACCEPTED&rideId=" + f.rideId(), f.driver())
                .andExpect(jsonPath("$.data.totalElements").value(seats));
    }

    @Test
    void concurrentDuplicateRequestsFromSamePassengerCreateOneBooking() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(pool.submit(() -> {
                startGate.await();
                try {
                    bookingService.create(passenger.id(),
                            new com.rideshare.booking.dto.CreateBookingRequest(f.rideId(), 1));
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        startGate.countDown();
        int created = 0;
        for (Future<Boolean> r : results) {
            if (r.get(30, TimeUnit.SECONDS)) {
                created++;
            }
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
    }
}
