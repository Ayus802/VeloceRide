package com.rideshare.location;

import com.rideshare.location.dto.LocationUpdateRequest;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end STOMP tests against the real embedded server. */
class LocationWebSocketIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUpClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        stompClient.stop();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                headers, new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        return queue;
    }

    /** Sends repeatedly until the first message arrives (the SUBSCRIBE may still be in flight). */
    private Map<String, Object> sendUntilReceived(Runnable send, BlockingQueue<Map<String, Object>> queue)
            throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            send.run();
            Map<String, Object> message = queue.poll(250, TimeUnit.MILLISECONDS);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    private static boolean waitForDisconnect(StompSession session) throws InterruptedException {
        for (int i = 0; i < 40 && session.isConnected(); i++) {
            Thread.sleep(100);
        }
        return !session.isConnected();
    }

    @Test
    void acceptedPassengerReceivesLiveDriverLocation() throws Exception {
        RideFixture f = rideFixture(3);
        TestUser passenger = registerUser();
        accept(f.driver(), requestBooking(passenger, f.rideId(), 1));
        startRide(f.driver(), f.rideId());

        StompSession passengerSession = connect(passenger.accessToken());
        BlockingQueue<Map<String, Object>> locations =
                subscribe(passengerSession, "/topic/rides/" + f.rideId() + "/location");
        StompSession driverSession = connect(f.driver().accessToken());

        Map<String, Object> received = sendUntilReceived(() -> driverSession.send("/app/location/update",
                new LocationUpdateRequest(f.rideId(), 28.6139, 77.2090, null)), locations);

        assertThat(received).isNotNull();
        assertThat(received.get("rideId")).isEqualTo(f.rideId().toString());
        assertThat(((Number) received.get("latitude")).doubleValue()).isEqualTo(28.6139);
        assertThat(((Number) received.get("longitude")).doubleValue()).isEqualTo(77.2090);
    }

    @Test
    void spoofedRideIdIsRejectedAndReportedOnErrorQueue() throws Exception {
        RideFixture victim = rideFixture(3);
        startRide(victim.driver(), victim.rideId());
        RideFixture attacker = rideFixture(3);
        startRide(attacker.driver(), attacker.rideId());

        StompSession attackerSession = connect(attacker.driver().accessToken());
        BlockingQueue<Map<String, Object>> errors = subscribe(attackerSession, "/user/queue/errors");
        Map<String, Object> error = sendUntilReceived(() -> attackerSession.send("/app/location/update",
                new LocationUpdateRequest(victim.rideId(), 10.0, 10.0, null)), errors);

        assertThat(error).isNotNull();
        assertThat(error.get("success")).isEqualTo(false);
        assertThat((String) error.get("message")).contains("Only the assigned driver");
        doGet("/api/rides/" + victim.rideId() + "/location/latest", victim.driver())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void outsiderCannotSubscribeToRideLocation() throws Exception {
        RideFixture f = rideFixture(3);
        startRide(f.driver(), f.rideId());
        TestUser outsider = registerUser();

        StompSession outsiderSession = connect(outsider.accessToken());
        BlockingQueue<Map<String, Object>> locations =
                subscribe(outsiderSession, "/topic/rides/" + f.rideId() + "/location");

        // The server answers the forbidden SUBSCRIBE with an ERROR frame and closes the session.
        assertThat(waitForDisconnect(outsiderSession)).isTrue();
        doPost("/api/rides/" + f.rideId() + "/location", f.driver(), Map.of("latitude", 1.0, "longitude", 1.0));
        assertThat(locations.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void cannotSubscribeToArbitraryTopics() throws Exception {
        TestUser user = registerUser();
        StompSession session = connect(user.accessToken());
        subscribe(session, "/topic/everything");
        assertThat(waitForDisconnect(session)).isTrue();
    }

    @Test
    void connectWithoutValidTokenIsRefused() throws Exception {
        for (String token : new String[]{null, "garbage"}) {
            try {
                StompSession session = connect(token);
                assertThat(waitForDisconnect(session)).isTrue();
            } catch (ExecutionException | TimeoutException expected) {
                // connection refused during CONNECT - also acceptable
            }
        }
    }
}
