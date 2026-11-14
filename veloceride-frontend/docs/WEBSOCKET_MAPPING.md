# WebSocket Mapping

Verified against `WebSocketConfig`, `StompAuthChannelInterceptor`, `LocationWebSocketController`,
`LocationServiceImpl`, `WebSocketNotificationChannel` and `rideshare-backend/docs/WEBSOCKET_API.md`.

## Connection

| Item | Value |
|---|---|
| Protocol | STOMP 1.2 over WebSocket |
| Native endpoint | `ws(s)://<host>/ws` → use `@stomp/stompjs` `brokerURL` |
| SockJS endpoint | `/ws-sockjs` — fallback only; **not used** (no `sockjs-client` dependency unless needed) |
| Origins | Same list as CORS (`FRONTEND_URL`) |
| Max inbound frame | 16 KB |
| Broker | In-memory simple broker (`/topic`, `/queue`), app prefix `/app`, user prefix `/user` |
| Heartbeats | Not configured server-side; client heartbeats are harmless but set to 0/0 or 10s/10s |

## Authentication

- HTTP handshake is public. The **STOMP `CONNECT` frame must carry `Authorization: Bearer <accessToken>`**.
- Invalid/expired token or deactivated account → `ERROR` frame and the socket closes.
- The token is checked **only at CONNECT**. When the REST layer refreshes tokens, the client should
  reconnect with the new token (use `client.beforeConnect` to read the current token from the auth store).

## Destinations

### Client → server

| SEND to | Who | Payload | Server rules |
|---|---|---|---|
| `/app/location/update` | Ride's driver | `{ rideId: UUID, latitude: number, longitude: number, timestamp?: LocalDateTime }` | driver only (403), ride ONGOING (409), coords valid (400), ≤1 per 2 s per ride (429), timestamp within ±5 min of server (omit it) |

SEND to anything other than `/app/**` is refused.

### Server → client

| SUBSCRIBE to | Allowed for | Payload |
|---|---|---|
| `/topic/rides/{rideId}/location` | Ride's driver, or passenger with **ACCEPTED** booking, while ride is SCHEDULED or ONGOING | `LocationResponse { rideId, latitude, longitude, timestamp, receivedAt }` |
| `/topic/rides/{rideId}/status` | Same | `TrackingStatusMessage { rideId, rideStatus, trackingActive }` — sent on start (true), complete/cancel (false) |
| `/user/queue/notifications` | Any authenticated user (own only) | `NotificationResponse` |
| `/user/queue/errors` | Any authenticated user (own only) | `{ success:false, message, errors:[], timestamp }` — errors from `/app/location/update` |

**An unauthorized SUBSCRIBE produces a STOMP `ERROR` and closes the whole connection** (not just that
subscription). The frontend must therefore only subscribe to ride topics after confirming eligibility via REST
(driver of the ride, or own booking with status ACCEPTED, ride SCHEDULED/ONGOING). Subscribing to a ride whose
status is COMPLETED/CANCELLED will also kill the connection.

## Frontend design

```
services/realtime/stompClient.ts   singleton Client; activate on login, deactivate on logout
  - brokerURL from VITE_WS_URL (e.g. ws://localhost:8080/ws)
  - beforeConnect: set connectHeaders.Authorization from auth store (fresh token every reconnect)
  - reconnectDelay: 5000 (exponential backoff wrapper, capped)
  - exposes connection state: 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'error'
  - re-subscribes registered subscriptions on every (re)connect
hooks/useNotificationsStream()     subscribes /user/queue/notifications → bumps unread count + toast
hooks/useRideTracking(rideId)      subscribes location + status; seeds from GET /location/latest (409/404 tolerated);
                                   marks location stale if receivedAt older than 30 s; unsubscribes on unmount
                                   or when trackingActive=false
hooks/useLocationSharing(rideId)   driver only, ride ONGOING: navigator.geolocation.watchPosition,
                                   throttled to one publish / 4 s; publishes via STOMP when connected,
                                   else REST POST /rides/{id}/location fallback; listens on /user/queue/errors;
                                   clearWatch on unmount, on trackingActive=false, or on complete
```

No polling replaces the WebSocket; REST `latest` is used only for the initial snapshot and the REST POST only
as a fallback when the socket is down.

## Lifecycle

1. Driver taps **Start ride** → `PATCH /api/rides/{id}/start` → server broadcasts `status{trackingActive:true}` and notifies passengers (`RIDE_STARTED`).
2. Driver console asks for geolocation permission, starts sharing.
3. Passengers on the tracking page receive `location` messages; map marker moves; "last update Xs ago".
4. Driver taps **Complete** → `status{trackingActive:false, rideStatus:COMPLETED}` → both sides stop, unsubscribe, show review CTA.
5. Cancel (before start) → `status{trackingActive:false, rideStatus:CANCELLED}`.
