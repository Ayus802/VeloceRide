# WebSocket API (STOMP)

Real-time features — **live driver location** and **live notifications** — use STOMP over WebSocket.
REST endpoints are documented in Swagger (`/swagger-ui/index.html`); this document covers only the WebSocket side.

## 1. Endpoints

| Endpoint     | Transport                         | Use                                                    |
|--------------|-----------------------------------|--------------------------------------------------------|
| `/ws`        | Native WebSocket (`ws://` / `wss://`) | Recommended (`@stomp/stompjs`)                    |
| `/ws-sockjs` | SockJS fallback                   | Only for environments where raw WebSockets are blocked |

Allowed browser origins are the same as CORS (`FRONTEND_URL`). Max inbound frame: 16 KB.

## 2. Authentication

The HTTP handshake is public; **every STOMP session must authenticate in the `CONNECT` frame** with the same
access token used for REST:

```
CONNECT
Authorization:Bearer <accessToken>
accept-version:1.2
```

- Missing, invalid, or expired token, or a deactivated account → the server replies with an `ERROR` frame and closes the connection.
- Access tokens are short-lived (default 15 min). When you refresh the token over REST, reconnect with the new token.

## 3. Destinations

### Client → server (`SEND`)

| Destination              | Who                         | Payload                  |
|--------------------------|-----------------------------|--------------------------|
| `/app/location/update`   | The ride's driver only      | `LocationUpdateRequest`  |

Clients cannot `SEND` to `/topic/**` or `/queue/**`; only `/app/**` is accepted.

### Server → client (`SUBSCRIBE`)

| Destination                        | Who can subscribe                                                  | Payload                 |
|------------------------------------|--------------------------------------------------------------------|-------------------------|
| `/topic/rides/{rideId}/location`   | The ride's driver, or a passenger with an **ACCEPTED** booking on it, while the ride is SCHEDULED or ONGOING | `LocationResponse` |
| `/topic/rides/{rideId}/status`     | Same as above                                                      | `TrackingStatusMessage` |
| `/user/queue/notifications`        | Any authenticated user (receives only their own)                  | `NotificationResponse`  |
| `/user/queue/errors`               | Any authenticated user (receives only their own)                  | Error envelope          |

Any other destination is refused: the server sends `ERROR` and closes the session. A user cannot listen to an
arbitrary ride's location.

## 4. Payloads

**LocationUpdateRequest** (driver → `/app/location/update`)
```json
{
  "rideId": "9b2f1c1e-6a7d-4c35-9a65-3f1d2b8c7e10",
  "latitude": 28.6139,
  "longitude": 77.2090,
  "timestamp": "2026-11-15T10:30:00"
}
```
- `latitude` must be in [-90, 90] and `longitude` in [-180, 180].
- `timestamp` is optional. It is the device time in the platform time zone (`APP_TIME_ZONE`) and must be within 5 minutes of server time.
- The server checks that the authenticated user is the driver of `rideId`. Sending another ride's id is rejected, so a driver cannot spoof another ride.
- Updates are accepted only while the ride is `ONGOING` (after `PATCH /api/rides/{id}/start`, before `/complete` or cancellation).
- Rate limit: one accepted update per ride every `LOCATION_MIN_UPDATE_INTERVAL` (default 2 s). Send every 3–5 s.

**LocationResponse** (broadcast on `/topic/rides/{rideId}/location`)
```json
{
  "rideId": "9b2f1c1e-6a7d-4c35-9a65-3f1d2b8c7e10",
  "latitude": 28.6139,
  "longitude": 77.209,
  "timestamp": "2026-11-15T05:00:00Z",
  "receivedAt": "2026-11-15T05:00:00.412Z"
}
```

**TrackingStatusMessage** (broadcast on `/topic/rides/{rideId}/status`)
```json
{ "rideId": "9b2f…", "rideStatus": "COMPLETED", "trackingActive": false }
```
Sent when the ride starts (`trackingActive: true`), completes, or is cancelled (`false`). On `false`, stop the
geolocation watcher and unsubscribe.

**Errors** (private, on `/user/queue/errors`)
```json
{ "success": false, "message": "Only the assigned driver can publish location for this ride", "errors": [], "timestamp": "2026-11-15T10:30:00" }
```

**NotificationResponse** (private, on `/user/queue/notifications`) — same shape as `GET /api/notifications` items.

## 5. React example (`@stomp/stompjs`)

```js
import { Client } from '@stomp/stompjs';

export function connectStomp(accessToken) {
  const client = new Client({
    brokerURL: 'ws://localhost:8080/ws',            // wss:// in production
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
  client.activate();
  return client;
}

// Passenger (or driver): watch the driver's location
client.onConnect = () => {
  client.subscribe(`/topic/rides/${rideId}/location`, (msg) => {
    const { latitude, longitude } = JSON.parse(msg.body);
    moveMarker(latitude, longitude);
  });
  client.subscribe(`/topic/rides/${rideId}/status`, (msg) => {
    if (!JSON.parse(msg.body).trackingActive) stopTracking();
  });
  client.subscribe('/user/queue/notifications', (msg) => addNotification(JSON.parse(msg.body)));
  client.subscribe('/user/queue/errors', (msg) => console.warn(JSON.parse(msg.body).message));
};

// Driver: share location with the Browser Geolocation API (requires the user's permission)
const watchId = navigator.geolocation.watchPosition(
  (pos) => client.publish({
    destination: '/app/location/update',
    body: JSON.stringify({ rideId, latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
  }),
  (err) => alert('Location permission is required to share your live location: ' + err.message),
  { enableHighAccuracy: true, maximumAge: 3000, timeout: 10000 },
);
// Throttle publishes to one every 3-5 s. Call navigator.geolocation.clearWatch(watchId) when the ride ends.
```

Only call `watchPosition` while the driver's ride is `ONGOING`. Never track a user who has no active ride.

## 6. REST companions

| Method | Path                                    | Purpose                                         |
|--------|-----------------------------------------|-------------------------------------------------|
| POST   | `/api/rides/{rideId}/location`          | Fallback for the driver to publish over HTTP (same rules) |
| GET    | `/api/rides/{rideId}/location/latest`   | Last known position (driver / accepted passengers) |
| GET    | `/api/rides/{rideId}/location/history`  | Stored history, paginated (driver / accepted or completed passengers) |

## 7. Scaling note

The default setup uses Spring's in-memory simple broker and an in-memory latest-location store, which is correct
for a single instance. To run several instances, switch to a STOMP broker relay (RabbitMQ or ActiveMQ) with
`registry.enableStompBrokerRelay(...)` and provide a Redis-backed `LatestLocationStore`.
