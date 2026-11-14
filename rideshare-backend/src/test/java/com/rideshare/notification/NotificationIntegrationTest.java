package com.rideshare.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIntegrationTest extends AbstractIntegrationTest {

    private List<String> types(TestUser user) throws Exception {
        JsonNode content = data(doGet("/api/notifications?size=50", user)).get("content");
        List<String> types = new ArrayList<>();
        content.forEach(n -> types.add(n.get("notificationType").asText()));
        return types;
    }

    @Test
    void notificationsAreGeneratedFromBusinessEvents() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        TestUser rejected = registerUser();
        UUID booking = requestBooking(passenger, f.rideId(), 1);
        UUID other = requestBooking(rejected, f.rideId(), 1);
        assertThat(types(f.driver())).containsOnly("BOOKING_REQUEST").hasSize(2);

        accept(f.driver(), booking);
        doPatch("/api/bookings/" + other + "/reject", f.driver(), null).andExpect(status().isOk());
        assertThat(types(passenger)).containsExactly("BOOKING_ACCEPTED");
        assertThat(types(rejected)).containsExactly("BOOKING_REJECTED");

        startRide(f.driver(), f.rideId());
        completeRide(f.driver(), f.rideId());
        assertThat(types(passenger)).contains("RIDE_STARTED", "RIDE_COMPLETED");
        assertThat(types(f.driver())).contains("RIDE_COMPLETED");
    }

    @Test
    void cancellationNotifiesTheOtherParty() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        UUID booking = requestBooking(passenger, f.rideId(), 1);
        doPatch("/api/bookings/" + booking + "/cancel", passenger, null).andExpect(status().isOk());
        assertThat(types(f.driver())).contains("BOOKING_CANCELLED");

        TestUser p2 = registerUser();
        accept(f.driver(), requestBooking(p2, f.rideId(), 1));
        doDelete("/api/rides/" + f.rideId(), f.driver()).andExpect(status().isOk());
        assertThat(types(p2)).contains("RIDE_CANCELLED");
    }

    @Test
    void markReadUnreadCountAndReadAll() throws Exception {
        RideFixture f = rideFixture(3);
        requestBooking(registerUser(), f.rideId(), 1);
        requestBooking(registerUser(), f.rideId(), 1);

        doGet("/api/notifications/unread-count", f.driver()).andExpect(jsonPath("$.data.unreadCount").value(2));
        String first = data(doGet("/api/notifications", f.driver())).at("/content/0/id").asText();
        doPatch("/api/notifications/" + first + "/read", f.driver(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));
        doGet("/api/notifications/unread-count", f.driver()).andExpect(jsonPath("$.data.unreadCount").value(1));
        doGet("/api/notifications?read=true", f.driver()).andExpect(jsonPath("$.data.totalElements").value(1));

        doPatch("/api/notifications/read-all", f.driver(), null).andExpect(status().isOk());
        doGet("/api/notifications/unread-count", f.driver()).andExpect(jsonPath("$.data.unreadCount").value(0));

        doDelete("/api/notifications/" + first, f.driver()).andExpect(status().isNoContent());
        doGet("/api/notifications", f.driver()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void usersCannotAccessOtherUsersNotifications() throws Exception {
        RideFixture f = rideFixture(3);
        requestBooking(registerUser(), f.rideId(), 1);
        String id = data(doGet("/api/notifications", f.driver())).at("/content/0/id").asText();
        TestUser intruder = registerUser();
        doPatch("/api/notifications/" + id + "/read", intruder, null).andExpect(status().isNotFound());
        doDelete("/api/notifications/" + id, intruder).andExpect(status().isNotFound());
        doGet("/api/notifications", intruder).andExpect(jsonPath("$.data.totalElements").value(0));
        doGet("/api/notifications", null).andExpect(status().isUnauthorized());
    }
}
