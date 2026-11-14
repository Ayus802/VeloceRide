package com.rideshare.review;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends AbstractIntegrationTest {

    private record Trip(RideFixture ride, TestUser p1, TestUser p2) {
    }

    private Trip completedTrip() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser p1 = registerUser("Passenger One");
        TestUser p2 = registerUser("Passenger Two");
        accept(f.driver(), requestBooking(p1, f.rideId(), 1));
        accept(f.driver(), requestBooking(p2, f.rideId(), 1));
        startRide(f.driver(), f.rideId());
        completeRide(f.driver(), f.rideId());
        return new Trip(f, p1, p2);
    }

    private Map<String, Object> review(UUID rideId, UUID reviewed, int rating) {
        return Map.of("rideId", rideId, "reviewedUserId", reviewed, "rating", rating, "comment", "Great trip");
    }

    @Test
    void participantsReviewEachOtherAndRatingIsAggregated() throws Exception {
        Trip t = completedTrip();
        UUID ride = t.ride().rideId();
        UUID driver = t.ride().driver().id();

        doPost("/api/reviews", t.p1(), review(ride, driver, 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewer.id").value(t.p1().id().toString()))
                .andExpect(jsonPath("$.data.rating").value(5));
        doPost("/api/reviews", t.p2(), review(ride, driver, 4)).andExpect(status().isCreated());
        doPost("/api/reviews", t.ride().driver(), review(ride, t.p1().id(), 3)).andExpect(status().isCreated());

        doGet("/api/users/" + driver + "/rating", t.p1())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.totalReviews").value(2));
        doGet("/api/users/" + driver + "/reviews", t.p1())
                .andExpect(jsonPath("$.data.rating.totalReviews").value(2))
                .andExpect(jsonPath("$.data.reviews.content.length()").value(2))
                .andExpect(jsonPath("$.data.reviews.content[0].reviewer.fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.reviews.content[0].createdAt").isNotEmpty());
        doGet("/api/rides/" + ride + "/reviews", t.p2()).andExpect(jsonPath("$.data.length()").value(3));
        doGet("/api/rides/" + ride, null).andExpect(jsonPath("$.data.driver.averageRating").value(4.5));
    }

    @Test
    void duplicateReviewIsRejected() throws Exception {
        Trip t = completedTrip();
        doPost("/api/reviews", t.p1(), review(t.ride().rideId(), t.ride().driver().id(), 5)).andExpect(status().isCreated());
        doPost("/api/reviews", t.p1(), review(t.ride().rideId(), t.ride().driver().id(), 4)).andExpect(status().isConflict());
    }

    @Test
    void nonParticipantCannotReview() throws Exception {
        Trip t = completedTrip();
        doPost("/api/reviews", registerUser(), review(t.ride().rideId(), t.ride().driver().id(), 5))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTargetsAreRejected() throws Exception {
        Trip t = completedTrip();
        UUID ride = t.ride().rideId();
        doPost("/api/reviews", t.p1(), review(ride, t.p1().id(), 5)).andExpect(status().isUnprocessableEntity());
        doPost("/api/reviews", t.p1(), review(ride, t.p2().id(), 5)).andExpect(status().isUnprocessableEntity());
        doPost("/api/reviews", t.ride().driver(), review(ride, registerUser().id(), 5))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reviewForIncompleteRideIsRejected() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        accept(f.driver(), requestBooking(passenger, f.rideId(), 1));
        doPost("/api/reviews", passenger, review(f.rideId(), f.driver().id(), 5))
                .andExpect(status().isUnprocessableEntity());
        startRide(f.driver(), f.rideId());
        doPost("/api/reviews", f.driver(), review(f.rideId(), passenger.id(), 5))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ratingMustBeBetweenOneAndFive() throws Exception {
        Trip t = completedTrip();
        doPost("/api/reviews", t.p1(), review(t.ride().rideId(), t.ride().driver().id(), 6))
                .andExpect(status().isBadRequest());
        doPost("/api/reviews", t.p1(), review(t.ride().rideId(), t.ride().driver().id(), 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanHideReviewAndItLeavesTheRating() throws Exception {
        Trip t = completedTrip();
        UUID reviewId = id(data(doPost("/api/reviews", t.p1(), review(t.ride().rideId(), t.ride().driver().id(), 1))));
        doPatch("/api/admin/reviews/" + reviewId + "/moderate", t.p1(), Map.of("hidden", true))
                .andExpect(status().isForbidden());
        doPatch("/api/admin/reviews/" + reviewId + "/moderate", admin(), Map.of("hidden", true, "note", "Abusive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true));
        doGet("/api/users/" + t.ride().driver().id() + "/rating", t.p1())
                .andExpect(jsonPath("$.data.totalReviews").value(0));
    }
}
