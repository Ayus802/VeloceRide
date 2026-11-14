package com.rideshare.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: a real PostgreSQL (Testcontainers) migrated by Flyway, the full Spring
 * context on a random port (needed for the WebSocket tests) and MockMvc. All subclasses share one cached context.
 * Tests create their own uniquely-named users, so no cleanup between tests is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private static final AtomicLong PHONE_SEQ = new AtomicLong(
            7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));

    protected static final String PASSWORD = "Secret@123";
    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected Clock clock;

    protected LocalDate inDays(int days) {
        return LocalDate.now(clock).plusDays(days);
    }

    @BeforeEach
    void resetCapturedMail(@Autowired TestSupportConfig.CapturingPasswordResetNotifier notifier) {
        notifier.clear();
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected ResultActions call(MockHttpServletRequestBuilder builder, TestUser user, Object body) throws Exception {
        if (user != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken());
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    protected ResultActions doGet(String url, TestUser user) throws Exception {
        return call(get(url), user, null);
    }

    protected ResultActions doPost(String url, TestUser user, Object body) throws Exception {
        return call(post(url), user, body);
    }

    protected ResultActions doPut(String url, TestUser user, Object body) throws Exception {
        return call(put(url), user, body);
    }

    protected ResultActions doPatch(String url, TestUser user, Object body) throws Exception {
        return call(patch(url), user, body);
    }

    protected ResultActions doDelete(String url, TestUser user) throws Exception {
        return call(delete(url), user, null);
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode data(ResultActions actions) throws Exception {
        return json(actions).get("data");
    }

    protected static UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    // ------------------------------------------------------------------ domain helpers

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 12) + "@test.com";
    }

    protected String uniquePhone() {
        return "+91" + PHONE_SEQ.incrementAndGet();
    }

    protected Map<String, Object> registration(String email, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Test User");
        body.put("email", email);
        body.put("phoneNumber", phone);
        body.put("password", PASSWORD);
        body.put("confirmPassword", PASSWORD);
        return body;
    }

    protected TestUser registerUser() throws Exception {
        return registerUser("Test User");
    }

    @Autowired
    protected TestSupportConfig.CapturingOtpDispatcher otp;

    /** Registers and completes email + SMS verification, returning a signed-in user. */
    protected TestUser registerUser(String fullName) throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        Map<String, Object> body = registration(email, phone);
        body.put("fullName", fullName);
        JsonNode flow = data(doPost("/api/auth/register", null, body).andExpect(status().isCreated()));
        String token = flow.at("/verification/verificationToken").asText();
        confirmCode(token, "EMAIL", otp.codeFor(email)).andExpect(status().isOk());
        JsonNode data = data(confirmCode(token, "PHONE", otp.codeFor(phone)).andExpect(status().isOk()))
                .get("auth");
        return new TestUser(UUID.fromString(data.at("/user/id").asText()), data.at("/user/email").asText(),
                data.get("accessToken").asText(), data.get("refreshToken").asText());
    }

    protected ResultActions confirmCode(String verificationToken, String channel, String code) throws Exception {
        return doPost("/api/auth/verification/confirm", null,
                Map.of("verificationToken", verificationToken, "channel", channel, "code", code));
    }

    protected TestUser login(String email, String password) throws Exception {
        JsonNode data = data(doPost("/api/auth/login", null, Map.of("email", email, "password", password))
                .andExpect(status().isOk()));
        return new TestUser(UUID.fromString(data.at("/user/id").asText()), email,
                data.get("accessToken").asText(), data.get("refreshToken").asText());
    }

    protected TestUser admin() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected Map<String, Object> vehicleBody(int seats) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleName", "My Car");
        body.put("vehicleNumber", "DL" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        body.put("vehicleType", "SEDAN");
        body.put("brand", "Honda");
        body.put("model", "City");
        body.put("color", "White");
        body.put("seatingCapacity", seats);
        return body;
    }

    protected UUID createVehicle(TestUser owner, int seats) throws Exception {
        return id(data(doPost("/api/vehicles", owner, vehicleBody(seats)).andExpect(status().isCreated())));
    }

    protected Map<String, Object> rideBody(UUID vehicleId, String source, String destination, LocalDate date,
                                           int seats, String price) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleId", vehicleId);
        body.put("source", source);
        body.put("destination", destination);
        body.put("sourceLatitude", 28.6139);
        body.put("sourceLongitude", 77.2090);
        body.put("destinationLatitude", 26.9124);
        body.put("destinationLongitude", 75.7873);
        body.put("departureDate", date.toString());
        body.put("departureTime", "09:30");
        body.put("totalSeats", seats);
        body.put("pricePerSeat", price);
        body.put("description", "AC car");
        return body;
    }

    protected UUID publishRide(TestUser driver, UUID vehicleId, String source, String destination, LocalDate date,
                               int seats) throws Exception {
        return id(data(doPost("/api/rides", driver, rideBody(vehicleId, source, destination, date, seats, "500.00"))
                .andExpect(status().isCreated())));
    }

    /** A driver with a vehicle and a ride 3 days from now with the given seats. */
    protected RideFixture rideFixture(int seats) throws Exception {
        TestUser driver = registerUser("Driver");
        UUID vehicle = createVehicle(driver, Math.max(seats, 4));
        UUID ride = publishRide(driver, vehicle, "Delhi", "Jaipur", inDays(3), seats);
        return new RideFixture(driver, vehicle, ride);
    }

    protected UUID requestBooking(TestUser passenger, UUID rideId, int seats) throws Exception {
        return id(data(doPost("/api/bookings", passenger, Map.of("rideId", rideId, "numberOfSeats", seats))
                .andExpect(status().isCreated())));
    }

    protected void accept(TestUser driver, UUID bookingId) throws Exception {
        doPatch("/api/bookings/" + bookingId + "/accept", driver, null).andExpect(status().isOk());
    }

    protected void startRide(TestUser driver, UUID rideId) throws Exception {
        doPatch("/api/rides/" + rideId + "/start", driver, null).andExpect(status().isOk());
    }

    protected void completeRide(TestUser driver, UUID rideId) throws Exception {
        doPatch("/api/rides/" + rideId + "/complete", driver, null).andExpect(status().isOk());
    }

    public record TestUser(UUID id, String email, String accessToken, String refreshToken) {
    }

    public record RideFixture(TestUser driver, UUID vehicleId, UUID rideId) {
    }
}
