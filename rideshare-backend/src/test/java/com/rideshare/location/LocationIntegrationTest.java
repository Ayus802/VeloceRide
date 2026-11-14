package com.rideshare.location;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationIntegrationTest extends AbstractIntegrationTest {

    private static final Map<String, Object> DELHI = Map.of("latitude", 28.6139, "longitude", 77.2090);

    private record Tracked(RideFixture ride, TestUser passenger) {
    }

    private Tracked ongoingRideWithPassenger() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        accept(f.driver(), requestBooking(passenger, f.rideId(), 1));
        startRide(f.driver(), f.rideId());
        return new Tracked(f, passenger);
    }

    @Test
    void onlyAssignedDriverCanPublishUpdates() throws Exception {
        Tracked t = ongoingRideWithPassenger();
        String url = "/api/rides/" + t.ride().rideId() + "/location";
        doPost(url, t.passenger(), DELHI).andExpect(status().isForbidden());

        // Another driver with their own ongoing ride cannot spoof this ride id.
        RideFixture other = rideFixture(2);
        startRide(other.driver(), other.rideId());
        doPost(url, other.driver(), DELHI).andExpect(status().isForbidden());

        doPost(url, t.ride().driver(), DELHI)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rideId").value(t.ride().rideId().toString()))
                .andExpect(jsonPath("$.data.latitude").value(28.6139));
    }

    @Test
    void invalidCoordinatesAreRejected() throws Exception {
        Tracked t = ongoingRideWithPassenger();
        String url = "/api/rides/" + t.ride().rideId() + "/location";
        doPost(url, t.ride().driver(), Map.of("latitude", 91.0, "longitude", 77.0)).andExpect(status().isBadRequest());
        doPost(url, t.ride().driver(), Map.of("latitude", 28.0, "longitude", -180.5)).andExpect(status().isBadRequest());
        doPost(url, t.ride().driver(), Map.of("latitude", 28.0)).andExpect(status().isBadRequest());
    }

    @Test
    void noTrackingWhenRideIsNotOngoing() throws Exception {
        RideFixture f = rideFixture(2);
        String url = "/api/rides/" + f.rideId() + "/location";
        doPost(url, f.driver(), DELHI).andExpect(status().isConflict());
        startRide(f.driver(), f.rideId());
        doPost(url, f.driver(), DELHI).andExpect(status().isOk());
        completeRide(f.driver(), f.rideId());
        doPost(url, f.driver(), DELHI).andExpect(status().isConflict());
        doGet(url + "/latest", f.driver()).andExpect(status().isConflict());
    }

    @Test
    void onlyAcceptedPassengersCanViewLocation() throws Exception {
        Tracked t = ongoingRideWithPassenger();
        UUID ride = t.ride().rideId();
        doPost("/api/rides/" + ride + "/location", t.ride().driver(), DELHI).andExpect(status().isOk());

        doGet("/api/rides/" + ride + "/location/latest", t.passenger())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.longitude").value(77.2090));
        doGet("/api/rides/" + ride + "/location/history", t.passenger())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        doGet("/api/rides/" + ride + "/location/latest", registerUser()).andExpect(status().isForbidden());
        doGet("/api/rides/" + ride + "/location/history", registerUser()).andExpect(status().isForbidden());
        doGet("/api/rides/" + ride + "/location/latest", null).andExpect(status().isUnauthorized());
    }
}
