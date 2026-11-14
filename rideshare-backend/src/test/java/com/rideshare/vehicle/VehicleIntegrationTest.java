package com.rideshare.vehicle;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VehicleIntegrationTest extends AbstractIntegrationTest {

    @Test
    void addVehicleNormalisesNumberAndStartsPending() throws Exception {
        TestUser owner = registerUser();
        Map<String, Object> body = vehicleBody(4);
        body.put("vehicleNumber", "mh 12-ab " + (1000 + (int) (Math.random() * 8999)));
        doPost("/api/vehicles", owner, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerId").value(owner.id().toString()))
                .andExpect(jsonPath("$.data.vehicleNumber").value(
                        ((String) body.get("vehicleNumber")).replaceAll("[\\s-]", "").toUpperCase()))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.isVerified").value(false));

        doGet("/api/vehicles", owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void duplicateVehicleNumberIsRejected() throws Exception {
        TestUser a = registerUser();
        TestUser b = registerUser();
        Map<String, Object> body = vehicleBody(4);
        doPost("/api/vehicles", a, body).andExpect(status().isCreated());
        doPost("/api/vehicles", b, body).andExpect(status().isConflict());
    }

    @Test
    void cannotViewEditOrDeleteAnotherUsersVehicle() throws Exception {
        TestUser owner = registerUser();
        TestUser intruder = registerUser();
        UUID vehicle = createVehicle(owner, 4);

        doPut("/api/vehicles/" + vehicle, intruder, vehicleBody(3)).andExpect(status().isForbidden());
        doDelete("/api/vehicles/" + vehicle, intruder).andExpect(status().isForbidden());
        doGet("/api/vehicles/" + vehicle, intruder).andExpect(status().isForbidden());

        doPut("/api/vehicles/" + vehicle, owner, vehicleBody(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatingCapacity").value(3));
    }

    @Test
    void adminVerifiesAndRejectsVehicles() throws Exception {
        TestUser owner = registerUser();
        TestUser admin = admin();
        UUID v1 = createVehicle(owner, 4);
        UUID v2 = createVehicle(owner, 4);

        doPatch("/api/admin/vehicles/" + v1 + "/verify", owner, null).andExpect(status().isForbidden());

        doGet("/api/admin/vehicles?status=PENDING&size=100", admin).andExpect(status().isOk());
        doPatch("/api/admin/vehicles/" + v1 + "/verify", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.isVerified").value(true));
        doPatch("/api/admin/vehicles/" + v2 + "/reject", admin, Map.of("reason", "Document unreadable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Document unreadable"));
    }

    @Test
    void cannotDeleteVehicleWithScheduledRide() throws Exception {
        RideFixture f = rideFixture(2);
        doDelete("/api/vehicles/" + f.vehicleId(), f.driver()).andExpect(status().isConflict());
        UUID unused = createVehicle(f.driver(), 4);
        doDelete("/api/vehicles/" + unused, f.driver()).andExpect(status().isNoContent());
        doGet("/api/vehicles/" + unused, f.driver()).andExpect(status().isNotFound());
    }
}
