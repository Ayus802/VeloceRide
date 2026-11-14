package com.rideshare.support;

import com.rideshare.booking.entity.Booking;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.enums.VehicleType;
import com.rideshare.ride.entity.Ride;
import com.rideshare.user.entity.User;
import com.rideshare.vehicle.entity.Vehicle;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** In-memory entity builders for unit tests. */
public final class Fixtures {

    private Fixtures() {
    }

    public static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static User user(String name) {
        User u = withId(new User(), UUID.randomUUID());
        u.setFullName(name);
        u.setEmail(name.toLowerCase().replace(' ', '.') + "@test.com");
        u.setPhoneNumber("+919999999999");
        u.setPassword("hash");
        return u;
    }

    public static Vehicle vehicle(User owner, int seats) {
        Vehicle v = withId(new Vehicle(), UUID.randomUUID());
        v.setOwner(owner);
        v.setVehicleName("Car");
        v.setVehicleNumber("DL01AB1234");
        v.setVehicleType(VehicleType.SEDAN);
        v.setBrand("Honda");
        v.setModel("City");
        v.setColor("White");
        v.setSeatingCapacity(seats);
        return v;
    }

    public static Ride ride(User driver, int totalSeats, int availableSeats, LocalDate date) {
        Ride r = withId(new Ride(), UUID.randomUUID());
        r.setDriver(driver);
        r.setVehicle(vehicle(driver, Math.max(totalSeats, 4)));
        r.setSource("Delhi");
        r.setDestination("Jaipur");
        r.setDepartureDate(date);
        r.setDepartureTime(LocalTime.of(9, 30));
        r.setTotalSeats(totalSeats);
        r.setAvailableSeats(availableSeats);
        r.setPricePerSeat(new BigDecimal("500.00"));
        r.setRideStatus(RideStatus.SCHEDULED);
        return r;
    }

    public static Booking booking(Ride ride, User passenger, int seats) {
        Booking b = withId(new Booking(), UUID.randomUUID());
        b.setRide(ride);
        b.setPassenger(passenger);
        b.setNumberOfSeats(seats);
        b.setTotalAmount(ride.getPricePerSeat().multiply(BigDecimal.valueOf(seats)));
        b.setBookingReference("BK-TEST" + seats);
        return b;
    }
}
