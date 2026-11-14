package com.rideshare;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The acceptance user journey from the specification, executed end-to-end over HTTP.
 */
class AcceptanceJourneyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fullUserJourney() throws Exception {
        // 1-2. A new user registers and logs in, receiving JWT tokens.
        TestUser registered = registerUser("Dev Driver");
        TestUser driver = login(registered.email(), PASSWORD);
        assertThat(driver.accessToken()).isNotBlank();
        assertThat(driver.refreshToken()).isNotBlank();

        // 3. The user updates their profile.
        doPut("/api/users/me", driver, Map.of("fullName", "Dev Kumar", "phoneNumber", uniquePhone(),
                "profilePicture", "https://cdn.example.com/dev.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Dev Kumar"));

        // 4. The user registers a vehicle.
        UUID vehicle = createVehicle(driver, 4);

        // 5. The user switches to RIDER mode.
        doPatch("/api/users/active-mode", driver, Map.of("activeMode", "RIDER"))
                .andExpect(jsonPath("$.data.activeMode").value("RIDER"));

        // 6. The user publishes a ride.
        String token = UUID.randomUUID().toString().substring(0, 6);
        String source = "Delhi" + token;
        String destination = "Jaipur" + token;
        LocalDate date = inDays(2);
        UUID ride = publishRide(driver, vehicle, source, destination, date, 3);

        // 7. Another user registers and logs in.
        TestUser passenger = registerUser("Pia Passenger");

        // 8. That user searches by source, destination and journey date.
        JsonNode results = data(doGet("/api/rides/search?source=" + source.toLowerCase()
                + "&destination=" + destination.toUpperCase() + "&date=" + date, passenger)
                .andExpect(status().isOk()));
        assertThat(results.at("/content/0/id").asText()).isEqualTo(ride.toString());
        assertThat(results.at("/content/0/driver/fullName").asText()).isEqualTo("Dev Kumar");

        // 9. The user requests seats.
        UUID booking = requestBooking(passenger, ride, 2);

        // 10. The driver receives a booking notification.
        doGet("/api/notifications", driver)
                .andExpect(jsonPath("$.data.content[0].notificationType").value("BOOKING_REQUEST"))
                .andExpect(jsonPath("$.data.content[0].referenceId").value(booking.toString()));

        // 11. The driver accepts the booking.
        accept(driver, booking);

        // 12. The passenger receives an acceptance notification.
        doGet("/api/notifications", passenger)
                .andExpect(jsonPath("$.data.content[0].notificationType").value("BOOKING_ACCEPTED"));

        // 13. Available seats are updated correctly.
        doGet("/api/rides/" + ride, null).andExpect(jsonPath("$.data.availableSeats").value(1));

        // 14. The driver starts the ride.
        startRide(driver, ride);

        // 15. Authorized passengers can view the live driver location; outsiders cannot.
        doPost("/api/rides/" + ride + "/location", driver, Map.of("latitude", 27.5, "longitude", 76.6))
                .andExpect(status().isOk());
        doGet("/api/rides/" + ride + "/location/latest", passenger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(27.5));
        doGet("/api/rides/" + ride + "/location/latest", registerUser()).andExpect(status().isForbidden());

        // 16. The driver completes the ride.
        completeRide(driver, ride);

        // 17. Both participants submit reviews.
        doPost("/api/reviews", passenger, Map.of("rideId", ride, "reviewedUserId", driver.id(), "rating", 5,
                "comment", "Excellent driver")).andExpect(status().isCreated());
        doPost("/api/reviews", driver, Map.of("rideId", ride, "reviewedUserId", passenger.id(), "rating", 4))
                .andExpect(status().isCreated());

        // 18. User ratings are updated.
        doGet("/api/users/" + driver.id() + "/rating", passenger)
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.totalReviews").value(1));
        doGet("/api/users/me", passenger).andExpect(jsonPath("$.data.averageRating").value(4.0));

        // 19. Notifications are stored and can be marked as read.
        doGet("/api/notifications/unread-count", driver)
                .andExpect(jsonPath("$.data.unreadCount").value(org.hamcrest.Matchers.greaterThan(0)));
        doPatch("/api/notifications/read-all", driver, null).andExpect(status().isOk());
        doGet("/api/notifications/unread-count", driver).andExpect(jsonPath("$.data.unreadCount").value(0));

        // 20. The same accounts switch roles: the passenger publishes, the driver books - no new accounts.
        doPatch("/api/users/active-mode", passenger, Map.of("activeMode", "RIDER")).andExpect(status().isOk());
        UUID passengersVehicle = createVehicle(passenger, 4);
        UUID returnRide = publishRide(passenger, passengersVehicle, destination, source, inDays(4), 2);
        doPatch("/api/users/active-mode", driver, Map.of("activeMode", "PASSENGER")).andExpect(status().isOk());
        doPost("/api/bookings", driver, Map.of("rideId", returnRide, "numberOfSeats", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passenger.id").value(driver.id().toString()))
                .andExpect(jsonPath("$.data.driver.id").value(passenger.id().toString()));

        // 21. Swagger documents the API.
        doGet("/v3/api-docs", null).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/rides/search']").exists());

        // 22. Protected endpoints reject unauthorized requests.
        doGet("/api/bookings/my-bookings", null).andExpect(status().isUnauthorized());
        doGet("/api/admin/stats", driver).andExpect(status().isForbidden());
    }
}
