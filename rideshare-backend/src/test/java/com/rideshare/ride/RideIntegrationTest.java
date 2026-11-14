package com.rideshare.ride;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RideIntegrationTest extends AbstractIntegrationTest {

    @Test
    void publishRideMakesCallerTheDriver() throws Exception {
        TestUser driver = registerUser("Rita Driver");
        UUID vehicle = createVehicle(driver, 4);
        doPost("/api/rides", driver, rideBody(vehicle, "Delhi", "Jaipur", inDays(5), 3, "650.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Ride published successfully"))
                .andExpect(jsonPath("$.data.driver.id").value(driver.id().toString()))
                .andExpect(jsonPath("$.data.driver.fullName").value("Rita Driver"))
                .andExpect(jsonPath("$.data.driver.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.vehicle.id").value(vehicle.toString()))
                .andExpect(jsonPath("$.data.availableSeats").value(3))
                .andExpect(jsonPath("$.data.rideStatus").value("SCHEDULED"));
    }

    @Test
    void publishValidations() throws Exception {
        TestUser driver = registerUser();
        UUID vehicle = createVehicle(driver, 3);
        TestUser other = registerUser();
        UUID othersVehicle = createVehicle(other, 4);

        doPost("/api/rides", driver, rideBody(othersVehicle, "Delhi", "Agra", inDays(2), 2, "100"))
                .andExpect(status().isForbidden());
        doPost("/api/rides", driver, rideBody(vehicle, "Delhi", "Agra", LocalDate.now(clock).minusDays(1), 2, "100"))
                .andExpect(status().isBadRequest());
        doPost("/api/rides", driver, rideBody(vehicle, "Delhi", "Agra", inDays(2), 2, "-1"))
                .andExpect(status().isBadRequest());
        doPost("/api/rides", driver, rideBody(vehicle, "Delhi", "Agra", inDays(2), 0, "100"))
                .andExpect(status().isBadRequest());
        doPost("/api/rides", driver, rideBody(vehicle, "Delhi", "Agra", inDays(2), 4, "100"))
                .andExpect(status().isUnprocessableEntity()); // exceeds vehicle capacity of 3
        doPost("/api/rides", driver, rideBody(vehicle, "Delhi", "delhi", inDays(2), 2, "100"))
                .andExpect(status().isBadRequest());
        doPost("/api/rides", null, rideBody(vehicle, "Delhi", "Agra", inDays(2), 2, "100"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateRideByDriver() throws Exception {
        RideFixture f = rideFixture(3);
        Map<String, Object> body = rideBody(f.vehicleId(), "Delhi", "Jaipur", inDays(3), 4, "700.00");
        body.put("description", "Updated");
        doPut("/api/rides/" + f.rideId(), f.driver(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSeats").value(4))
                .andExpect(jsonPath("$.data.availableSeats").value(4))
                .andExpect(jsonPath("$.data.pricePerSeat").value(700.00))
                .andExpect(jsonPath("$.data.description").value("Updated"));
    }

    @Test
    void preventUnauthorizedRideUpdatesAndCancellation() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser intruder = registerUser();
        UUID intruderVehicle = createVehicle(intruder, 4);
        doPut("/api/rides/" + f.rideId(), intruder, rideBody(intruderVehicle, "Delhi", "Jaipur", inDays(3), 2, "1"))
                .andExpect(status().isForbidden());
        doDelete("/api/rides/" + f.rideId(), intruder).andExpect(status().isForbidden());
        doPatch("/api/rides/" + f.rideId() + "/start", intruder, null).andExpect(status().isForbidden());
    }

    @Test
    void committedTermsAreLockedOnceBooked() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        accept(f.driver(), requestBooking(passenger, f.rideId(), 2));

        doPut("/api/rides/" + f.rideId(), f.driver(), rideBody(f.vehicleId(), "Delhi", "Jaipur", inDays(3), 3, "999"))
                .andExpect(status().isConflict());
        doPut("/api/rides/" + f.rideId(), f.driver(), rideBody(f.vehicleId(), "Delhi", "Jaipur", inDays(3), 1, "500.00"))
                .andExpect(status().isUnprocessableEntity()); // below the 2 booked seats
        doPut("/api/rides/" + f.rideId(), f.driver(), rideBody(f.vehicleId(), "Delhi", "Jaipur", inDays(3), 4, "500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableSeats").value(2))
                .andExpect(jsonPath("$.data.bookedSeats").value(2));
    }

    @Test
    void searchBySourceDestinationAndDateIsCaseInsensitiveAndExcludesUnavailableRides() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 8);
        String src = "Srcity" + token;
        String dst = "Dstcity" + token;
        LocalDate date = inDays(6);
        TestUser driver = registerUser();
        UUID vehicle = createVehicle(driver, 4);

        UUID matching = publishRide(driver, vehicle, src, dst, date, 3);
        UUID otherDate = publishRide(driver, vehicle, src, dst, date.plusDays(1), 3);
        UUID cancelled = publishRide(driver, vehicle, src, dst, date, 3);
        doDelete("/api/rides/" + cancelled, driver).andExpect(status().isOk());
        UUID full = publishRide(driver, vehicle, src, dst, date, 1);
        accept(driver, requestBooking(registerUser(), full, 1));
        UUID reverse = publishRide(driver, vehicle, dst, src, date, 3);

        JsonNode page = data(doGet("/api/rides/search?source=" + src.toUpperCase() + "&destination="
                + dst.toLowerCase() + "&date=" + date, null).andExpect(status().isOk()));
        List<String> ids = new ArrayList<>();
        page.get("content").forEach(r -> ids.add(r.get("id").asText()));

        assertThat(ids).containsExactly(matching.toString());
        assertThat(ids).doesNotContain(otherDate.toString(), cancelled.toString(), full.toString(), reverse.toString());
        JsonNode ride = page.get("content").get(0);
        assertThat(ride.at("/driver/id").asText()).isEqualTo(driver.id().toString());
        assertThat(ride.at("/vehicle/brand").asText()).isEqualTo("Honda");
        assertThat(ride.get("availableSeats").asInt()).isEqualTo(3);
        assertThat(ride.get("pricePerSeat").decimalValue()).isEqualByComparingTo("500.00");
    }

    @Test
    void searchValidatesParametersAndSort() throws Exception {
        doGet("/api/rides/search?source=Delhi&date=" + inDays(1), null).andExpect(status().isBadRequest());
        doGet("/api/rides/search?source=Delhi&destination=Jaipur&date=not-a-date", null).andExpect(status().isBadRequest());
        doGet("/api/rides/search?source=Delhi&destination=Jaipur&date=" + LocalDate.now(clock).minusDays(1), null)
                .andExpect(status().isBadRequest());
        doGet("/api/rides/search?source=Delhi&destination=Jaipur&date=" + inDays(1) + "&sort=driver.password", null)
                .andExpect(status().isBadRequest());
        doGet("/api/rides/search?source=Delhi&destination=Jaipur&date=" + inDays(1) + "&sort=pricePerSeat,desc&size=5", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    void searchTreatsLikeWildcardsLiterally() throws Exception {
        rideFixture(2); // a Delhi -> Jaipur ride on that date that an unescaped "_" pattern would match
        doGet("/api/rides/search?source=__&destination=__&date=" + inDays(3), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void myRidesAndPublicDetails() throws Exception {
        RideFixture f = rideFixture(2);
        doGet("/api/rides/my-rides", f.driver())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(f.rideId().toString()));
        doGet("/api/rides/my-rides", null).andExpect(status().isUnauthorized());
        doGet("/api/rides/" + f.rideId(), null).andExpect(status().isOk());
        doGet("/api/rides/" + UUID.randomUUID(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ride not found"));
    }

    @Test
    void rideLifecycleTransitions() throws Exception {
        RideFixture f = rideFixture(2);
        doPatch("/api/rides/" + f.rideId() + "/complete", f.driver(), null).andExpect(status().isConflict());
        startRide(f.driver(), f.rideId());
        doPut("/api/rides/" + f.rideId(), f.driver(), rideBody(f.vehicleId(), "Delhi", "Jaipur", inDays(3), 2, "500"))
                .andExpect(status().isConflict());
        doDelete("/api/rides/" + f.rideId(), f.driver()).andExpect(status().isConflict());
        completeRide(f.driver(), f.rideId());
        doGet("/api/rides/" + f.rideId(), null).andExpect(jsonPath("$.data.rideStatus").value("COMPLETED"));
    }
}
