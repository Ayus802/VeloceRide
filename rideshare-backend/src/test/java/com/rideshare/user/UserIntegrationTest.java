package com.rideshare.user;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIntegrationTest extends AbstractIntegrationTest {

    @Test
    void profileRetrieval() throws Exception {
        TestUser user = registerUser("Asha Verma");
        doGet("/api/users/me", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.id().toString()))
                .andExpect(jsonPath("$.data.fullName").value("Asha Verma"))
                .andExpect(jsonPath("$.data.totalReviews").value(0))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void unauthenticatedProfileAccessIsRejected() throws Exception {
        doGet("/api/users/me", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void profileUpdate() throws Exception {
        TestUser user = registerUser();
        String phone = uniquePhone();
        doPut("/api/users/me", user, Map.of("fullName", "New Name", "phoneNumber", phone,
                "profilePicture", "https://cdn.example.com/p.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("New Name"))
                .andExpect(jsonPath("$.data.phoneNumber").value(phone))
                .andExpect(jsonPath("$.data.profilePicture").value("https://cdn.example.com/p.jpg"));
    }

    @Test
    void profileUpdateRejectsPhoneOfAnotherUserAndBadUrl() throws Exception {
        TestUser a = registerUser();
        TestUser b = registerUser();
        String bPhone = data(doGet("/api/users/me", b)).get("phoneNumber").asText();
        doPut("/api/users/me", a, Map.of("fullName", "Abc", "phoneNumber", bPhone))
                .andExpect(status().isConflict());
        doPut("/api/users/me", a, Map.of("fullName", "Abc", "phoneNumber", uniquePhone(), "profilePicture", "javascript:x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modeSwitchingKeepsSameAccountAndNeverRestrictsActions() throws Exception {
        TestUser user = registerUser();
        doPatch("/api/users/active-mode", user, Map.of("activeMode", "RIDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeMode").value("RIDER"))
                .andExpect(jsonPath("$.data.id").value(user.id().toString()));

        // In RIDER mode the same account can still book someone else's ride...
        RideFixture other = rideFixture(3);
        requestBooking(user, other.rideId(), 1);

        doPatch("/api/users/active-mode", user, Map.of("activeMode", "PASSENGER"))
                .andExpect(jsonPath("$.data.activeMode").value("PASSENGER"))
                .andExpect(jsonPath("$.data.id").value(user.id().toString()));

        // ...and in PASSENGER mode it can still publish a ride.
        UUID vehicle = createVehicle(user, 4);
        publishRide(user, vehicle, "Pune", "Mumbai", inDays(4), 2);
    }

    @Test
    void invalidModeIsRejected() throws Exception {
        TestUser user = registerUser();
        doPatch("/api/users/active-mode", user, Map.of("activeMode", "DRIVER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        TestUser user = registerUser();
        doPatch("/api/users/me/password", user, Map.of("currentPassword", "Wrong@123",
                "newPassword", "Newpass@123", "confirmPassword", "Newpass@123"))
                .andExpect(status().isBadRequest());
        doPatch("/api/users/me/password", user, Map.of("currentPassword", PASSWORD,
                "newPassword", "Newpass@123", "confirmPassword", "Newpass@123"))
                .andExpect(status().isOk());
        login(user.email(), "Newpass@123");
    }

    @Test
    void publicProfileHidesContactDetails() throws Exception {
        TestUser viewer = registerUser();
        TestUser other = registerUser("Other Person");
        doGet("/api/users/" + other.id(), viewer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Other Person"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist());
    }

    @Test
    void deactivatedAccountCannotUseTokensOrLogin() throws Exception {
        TestUser user = registerUser();
        doPatch("/api/users/me/deactivate", user, null).andExpect(status().isOk());
        doGet("/api/users/me", user)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is deactivated"));
        doPost("/api/auth/login", null, Map.of("email", user.email(), "password", PASSWORD))
                .andExpect(status().isForbidden());
    }
}
