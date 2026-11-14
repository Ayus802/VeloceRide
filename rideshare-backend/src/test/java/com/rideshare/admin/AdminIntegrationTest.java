package com.rideshare.admin;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTest extends AbstractIntegrationTest {

    @Test
    void normalUsersCannotAccessAdminApis() throws Exception {
        TestUser user = registerUser();
        for (String url : new String[]{"/api/admin/users", "/api/admin/rides", "/api/admin/bookings",
                "/api/admin/vehicles", "/api/admin/reviews", "/api/admin/stats"}) {
            doGet(url, user).andExpect(status().isForbidden());
            doGet(url, null).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void adminListsAndDeactivatesUsers() throws Exception {
        TestUser admin = admin();
        TestUser user = registerUser();
        doGet("/api/admin/users?q=" + user.email(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist());
        doGet("/api/admin/users/" + user.id(), admin).andExpect(jsonPath("$.data.email").value(user.email()));

        doPatch("/api/admin/users/" + user.id() + "/status", admin, Map.of("accountStatus", "DEACTIVATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("DEACTIVATED"));
        doGet("/api/users/me", user).andExpect(status().isUnauthorized());
        doPost("/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken())).andExpect(status().isUnauthorized());

        doPatch("/api/admin/users/" + user.id() + "/status", admin, Map.of("accountStatus", "ACTIVE"))
                .andExpect(status().isOk());
        login(user.email(), PASSWORD);

        doPatch("/api/admin/users/" + admin.id() + "/status", admin, Map.of("accountStatus", "DEACTIVATED"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminSeesRidesBookingsReviewsAndStats() throws Exception {
        TestUser admin = admin();
        RideFixture f = rideFixture(2);
        requestBooking(registerUser(), f.rideId(), 1);
        doGet("/api/admin/rides?status=SCHEDULED", admin).andExpect(status().isOk());
        doGet("/api/admin/bookings?status=PENDING", admin).andExpect(status().isOk());
        doGet("/api/admin/reviews", admin).andExpect(status().isOk());
        doGet("/api/admin/stats", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.ridesByStatus.SCHEDULED").isNumber());
    }
}
