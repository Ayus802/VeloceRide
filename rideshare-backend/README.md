# RideShare Backend

A Spring Boot backend for a BlaBlaCar-style ride-sharing web app.

> **One account, one profile, one user id, two modes.** Every user registers once and can both **publish rides
> (RIDER mode)** and **book seats (PASSENGER mode)**. `activeMode` is only a UI preference. Permissions come from
> **ownership** (driver of this ride, passenger of this booking, owner of this vehicle) and the system **ADMIN** role.

| | |
|---|---|
| Stack | Java 17, Spring Boot 3.4.5, Spring Security + JWT (jjwt 0.12), Spring Data JPA/Hibernate 6, PostgreSQL 16, Flyway, Spring WebSocket/STOMP, springdoc-openapi 2.8, Lombok, Actuator |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL), a real STOMP client: 99 tests |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| WebSocket docs | [docs/WEBSOCKET_API.md](docs/WEBSOCKET_API.md) |

---

## 1. Quick start

### Option A: Docker Compose (database and app)

```bash
cp .env.example .env          # then set JWT_SECRET, DB_PASSWORD, ADMIN_* ...
docker compose up --build
```

The app runs with the `prod` profile on http://localhost:8080. Flyway creates the schema on the empty database.

### Option B: Run locally (dev profile)

Requirements: JDK 17+ and Docker (or your own PostgreSQL). Maven is not needed because the wrapper downloads it.

```bash
docker compose up -d postgres        # PostgreSQL on localhost:5432 (rideshare/rideshare)
./mvnw spring-boot:run               # Windows: mvnw.cmd spring-boot:run
```

The `dev` profile is the default. It ships safe local defaults:

| Setting | dev default |
|---|---|
| DB | `jdbc:postgresql://localhost:5432/rideshare`, user/password `rideshare` |
| JWT secret | a dev-only secret (production requires `JWT_SECRET`) |
| CORS | `http://localhost:3000`, `http://localhost:5173` |
| Admin | `admin@rideshare.local` / `Admin@12345` (created on first start) |
| Vehicle verification | **disabled**, so you can publish rides right after adding a vehicle |
| Ride start window | 30 days before departure (prod: 2 h), for easy manual testing |
| Password reset | the reset link is **logged** (prod: never logged) |

### Option C: Build a jar

```bash
./mvnw clean package          # runs all tests (needs Docker for Testcontainers)
java -jar target/rideshare-backend-1.0.0.jar --spring.profiles.active=prod
```

---

## 2. Configuration

All configuration comes from environment variables. See [.env.example](.env.example).

| Variable | Required in prod | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | | `dev` (default) or `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | ✔ | PostgreSQL connection |
| `JWT_SECRET` | ✔ | HS256 key, **at least 32 bytes**. Startup fails if it is missing or too short |
| `JWT_ACCESS_EXPIRATION` | | ISO-8601 duration, default `PT15M` |
| `JWT_REFRESH_EXPIRATION` | | default `P7D` |
| `FRONTEND_URL` | ✔ | Allowed origin(s) for CORS and the WebSocket handshake, comma separated, no wildcard |
| `SERVER_PORT` | | default `8080` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | | Initial admin, created once if that email does not exist yet |
| `APP_TIME_ZONE` | | Zone used to interpret ride dates and times (default `Asia/Kolkata`) |
| `VEHICLE_VERIFICATION_REQUIRED` | | `true` in prod: only admin-verified vehicles can publish rides |
| `LOCATION_MIN_UPDATE_INTERVAL` | | Per-ride rate limit for location updates (default `PT2S`) |
| `LOCATION_HISTORY_ENABLED` | | Persist location history (default `true`) |
| `SWAGGER_ENABLED` | | Expose Swagger in prod (default `true`) |

The `prod` profile contains **no secret defaults**. Hibernate runs with `ddl-auto=validate`; Flyway owns the schema.

---

## 3. Architecture

A modular monolith with a layered structure in each module (controller → service interface/impl → repository → entity, with DTOs at the edges).

```
com.rideshare
├── RideShareApplication
├── config/        SecurityConfig, CorsConfig, WebSocketConfig, OpenApiConfig, JwtConfig, AppProperties, ClockConfig
├── security/      JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetailsService, UserPrincipal,
│                  StompAuthChannelInterceptor, RestAuthenticationHandlers
├── common/        response/ (ApiResponse, PageResponse), exception/ (GlobalExceptionHandler + typed exceptions),
│                  enums/, event/ (domain events), entity/BaseEntity, util/, validation/
├── auth/          register/login/refresh/logout/forgot/reset, refresh & reset tokens
├── user/          unified profile, active-mode switch, password change
├── vehicle/       vehicles and admin verification
├── ride/          publish, update, cancel, start, complete, search
├── booking/       request → accept/reject/cancel workflow, seat allocation
├── payment/       payment rules + PaymentGateway port (SimulatedPaymentGateway)
├── review/        reviews + RatingService (aggregate ratings)
├── notification/  in-app notifications, event listener, WebSocket channel
├── location/      live tracking (STOMP + REST), history, latest-location store
└── admin/         admin APIs, initial admin bootstrap
```

**Decoupling with domain events.** Booking, ride, payment and review services publish events (`BookingEvent`,
`RideEvent`, `PaymentEvent`, `ReviewEvent`). Notifications, payment refunds and location start/stop are listeners.
Listeners run synchronously inside the same transaction, so a notification exists only if the business change
committed. WebSocket pushes happen **after commit**.

**IDs.** UUIDs everywhere, in the database and the API.

### Database (Flyway `src/main/resources/db/migration`)

`users`, `vehicles`, `rides`, `bookings`, `payments`, `reviews`, `notifications`, `ride_locations`,
`refresh_tokens`, `password_reset_tokens`. There is one `users` table for drivers and passengers.
Enums are `VARCHAR` with `CHECK` constraints. Important constraints:

- `rides.available_seats BETWEEN 0 AND total_seats`: a database backstop against overbooking
- partial unique index: one live (PENDING/ACCEPTED) booking per passenger per ride
- partial unique index: one PENDING/SUCCESS payment per booking
- unique `(ride_id, reviewer_id, reviewed_user_id)` on reviews; `CHECK (reviewer_id <> reviewed_user_id)`
- partial unique vehicle number among non-deleted vehicles
- search index `(departure_date, ride_status, lower(source), lower(destination))`

---

## 4. Key business rules

**Rides.** The logged-in user is always the driver. The vehicle must be the caller's own (403). When verification is enabled, the vehicle must also be verified (422). Departure must be in the future, total seats cannot exceed vehicle capacity, and the price must not be negative. Once requests or bookings exist, the route, schedule, price and vehicle are locked (409), and seats cannot drop below the number already booked (422). Lifecycle: `SCHEDULED → ONGOING → COMPLETED`, or `SCHEDULED → CANCELLED`. Cancelling a ride cancels every booking, notifies the passengers, and refunds payments. Starting a ride auto-rejects unanswered requests.

**Search.** `GET /api/rides/search?source=&destination=&date=` are the only required filters. Matching is case-insensitive "contains", and LIKE wildcards in input are escaped. Results include only `SCHEDULED` rides that have free seats and have not yet departed. Results are paginated and sortable by `departureTime` (default), `pricePerSeat`, `availableSeats` or `createdAt`. Driver and vehicle are fetched in the same query, and driver ratings for the whole page come from one aggregate query.

**Bookings.** A request starts `PENDING` and holds no seats. The driver accepts (seats allocated) or rejects. The passenger can cancel a `PENDING` or `ACCEPTED` booking until the ride starts; accepted seats are released and a successful payment is refunded. You cannot book your own ride, and you cannot request more seats than are available.

**Concurrency.** Every seat-changing operation locks the **ride row** first (`SELECT … FOR UPDATE`), then the booking row, always in that order. Concurrent accepts are serialized, so a seat is never allocated twice. Tests cover 8 concurrent accepts on a 3-seat ride (exactly 3 succeed) and concurrent duplicate requests (exactly 1 succeeds).

**Payments.** The amount always comes from the booking. The flow is `initiate` (PENDING, provider order) then `confirm` (server-side verification through `PaymentGateway`). The confirm request has no "success" flag. CASH payments are confirmed by the **driver** after collecting the cash. The bundled provider is `SIMULATED`: **no real money moves**. Sending `providerPaymentId: "SIMULATE_FAILURE"` simulates a decline, like a sandbox test card. To add Razorpay, implement `PaymentGateway` (create order, verify the HMAC signature, refund) and select it with `app.payment.provider`.

**Reviews.** Only after a ride is `COMPLETED`. Only the driver and passengers whose booking completed can review. The driver reviews passengers and passengers review the driver; nobody can review themselves; one review per (ride, reviewer, reviewed user). Ratings must be 1 to 5. Admins can hide abusive reviews, and hidden reviews are excluded from ratings.

**Live location.** Only the ride's driver can publish, and only while the ride is `ONGOING`. Coordinates are validated and updates are rate-limited. Only the driver and passengers with an `ACCEPTED` booking can subscribe or view. See [docs/WEBSOCKET_API.md](docs/WEBSOCKET_API.md).

---

## 5. API overview

All responses use one envelope:

```json
{ "success": true,  "message": "Ride published successfully", "data": { }, "timestamp": "2026-11-15T10:30:00" }
{ "success": false, "message": "Ride not found", "errors": [], "timestamp": "2026-11-15T10:30:00" }
```

Status codes: 200, 201, 204, 400 (validation), 401 (no/invalid token), 403 (not owner/admin), 404, 409 (duplicate
or wrong state), 422 (business rule, e.g. not enough seats), 429 (location rate limit), 500 (no internals leaked).

List endpoints accept `page` (0-based), `size` (max 100) and `sort=field,asc|desc`. Unknown sort fields return 400.

| Module | Endpoints |
|---|---|
| Auth (public) | `POST /api/auth/register`, `/login`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password` |
| Users | `GET/PUT /api/users/me`, `PATCH /api/users/me/password`, `PATCH /api/users/active-mode`, `PATCH /api/users/me/deactivate`, `GET /api/users/{id}` |
| Vehicles | `POST/GET /api/vehicles`, `GET/PUT/DELETE /api/vehicles/{id}` |
| Rides | `POST /api/rides`, `GET /api/rides` (public), `GET /api/rides/search` (public), `GET /api/rides/{id}` (public), `PUT/DELETE /api/rides/{id}`, `GET /api/rides/my-rides`, `PATCH /api/rides/{id}/start`, `PATCH /api/rides/{id}/complete` |
| Bookings | `POST /api/bookings`, `GET /api/bookings/{id}`, `GET /api/bookings/my-bookings`, `GET /api/bookings/received-requests`, `PATCH /api/bookings/{id}/accept`, `/reject`, `/cancel` |
| Payments | `POST /api/payments/initiate`, `POST /api/payments/confirm`, `GET /api/payments/{id}`, `GET /api/payments/my-payments` |
| Reviews | `POST /api/reviews`, `GET /api/users/{id}/reviews`, `GET /api/users/{id}/rating`, `GET /api/rides/{id}/reviews` |
| Notifications | `GET /api/notifications?read=`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all`, `DELETE /api/notifications/{id}` |
| Location | `POST /api/rides/{id}/location`, `GET /api/rides/{id}/location/latest`, `GET /api/rides/{id}/location/history`; STOMP `/ws` |
| Admin | `GET /api/admin/users[?q=]`, `GET /api/admin/users/{id}`, `PATCH /api/admin/users/{id}/status`, `GET /api/admin/rides`, `GET /api/admin/bookings`, `GET /api/admin/vehicles[?status=]`, `PATCH /api/admin/vehicles/{id}/verify`, `PATCH /api/admin/vehicles/{id}/reject`, `GET /api/admin/reviews`, `PATCH /api/admin/reviews/{id}/moderate`, `GET /api/admin/stats` |

### Example walkthrough (curl)

```bash
API=http://localhost:8080

# Register (returns tokens)
curl -s -X POST $API/api/auth/register -H 'Content-Type: application/json' -d '{
  "fullName":"Asha Verma","email":"asha@example.com","phoneNumber":"+919876543210",
  "password":"Secret@123","confirmPassword":"Secret@123"}'

# Login
TOKEN=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"Secret@123"}' | jq -r .data.accessToken)

# Add a vehicle
VEHICLE=$(curl -s -X POST $API/api/vehicles -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "vehicleName":"My Swift","vehicleNumber":"DL 01 AB 1234","vehicleType":"HATCHBACK",
  "brand":"Maruti Suzuki","model":"Swift","color":"White","seatingCapacity":4}' | jq -r .data.id)

# Switch the UI to RIDER mode (optional; it never restricts actions)
curl -s -X PATCH $API/api/users/active-mode -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"activeMode":"RIDER"}'

# Publish a ride
curl -s -X POST $API/api/rides -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{
  \"vehicleId\":\"$VEHICLE\",\"source\":\"Delhi\",\"destination\":\"Jaipur\",
  \"departureDate\":\"2026-11-15\",\"departureTime\":\"09:30\",\"totalSeats\":3,\"pricePerSeat\":650}"

# Search (public)
curl -s "$API/api/rides/search?source=delhi&destination=JAIPUR&date=2026-11-15&sort=pricePerSeat,asc"

# As another user: request seats; then as the driver: accept
curl -s -X POST $API/api/bookings -H "Authorization: Bearer $PASSENGER_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"rideId\":\"$RIDE\",\"numberOfSeats\":2}"
curl -s -X PATCH $API/api/bookings/$BOOKING/accept -H "Authorization: Bearer $TOKEN"
```

---

## 6. Connecting a React frontend

1. **Base URL.** Point the frontend at `http://localhost:8080` (dev) and set `FRONTEND_URL` on the backend to the frontend's origin.
2. **Auth.** Keep the `accessToken` in memory and send `Authorization: Bearer <token>`. Store the `refreshToken` securely and call `/api/auth/refresh` on 401. Refresh tokens are **single use**: always save the new one.
3. **One account, two modes.** Show a RIDER/PASSENGER toggle that calls `PATCH /api/users/active-mode` and switches dashboards. Do not create separate sign-ups.

```js
// api.js (axios)
import axios from 'axios';
export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080' });

let accessToken = null;
export const setAccessToken = (t) => { accessToken = t; };
api.interceptors.request.use((cfg) => {
  if (accessToken) cfg.headers.Authorization = `Bearer ${accessToken}`;
  return cfg;
});

let refreshing = null;
api.interceptors.response.use((r) => r, async (error) => {
  const original = error.config;
  if (error.response?.status === 401 && !original._retry && localStorage.getItem('refreshToken')) {
    original._retry = true;
    refreshing ??= axios.post(`${api.defaults.baseURL}/api/auth/refresh`,
        { refreshToken: localStorage.getItem('refreshToken') })
      .then(({ data }) => {
        setAccessToken(data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);   // rotated!
      })
      .finally(() => { refreshing = null; });
    await refreshing;
    return api(original);
  }
  // error.response.data = { success:false, message, errors:[{field,message}] }
  return Promise.reject(error);
});
```

4. **Validation errors.** `errors[].field` matches the request field names, so you can map them straight onto form inputs.
5. **Live tracking and notifications.** Use `@stomp/stompjs` with `ws://localhost:8080/ws`. See the full example in [docs/WEBSOCKET_API.md](docs/WEBSOCKET_API.md).
6. **Date and time formats.** `departureDate` is `YYYY-MM-DD`, `departureTime` is `HH:mm` or `HH:mm:ss` (platform time zone), and audit timestamps are ISO-8601 UTC instants.

---

## 7. Testing

```bash
./mvnw test
```

Integration tests start PostgreSQL 16 in Docker (Testcontainers) and run Flyway on the empty database. They cover:

- auth: registration, duplicates, login, wrong password, invalid/expired/forged tokens, refresh rotation and reuse detection, logout, password reset
- users: profile, update, mode switching that keeps the same account, unauthorized access, deactivation
- vehicles: create, ownership enforcement, admin verify/reject, delete rules
- rides: publish validations, update, unauthorized updates, terms locked after booking, search (case-insensitive, excludes cancelled, full, other-date and past rides; escapes wildcards), lifecycle
- bookings: create, insufficient seats, own ride, duplicates, accept/reject/cancel, seat release, **concurrent accepts never overbook**, concurrent duplicate requests
- payments: server-side verification, decline, cash confirmed by driver, refund on cancellation
- reviews: valid, non-participant, incomplete ride, duplicate, rating range, aggregation, moderation
- notifications: generated from events, ownership (404 for others), read, read-all, delete
- location: driver-only publish, anti-spoofing, coordinate validation, no tracking outside ONGOING, viewer authorization; a **real STOMP client** checks delivery, denied subscriptions and refused CONNECTs
- admin: access control, user (de)activation, stats
- `AcceptanceJourneyIntegrationTest` runs the complete acceptance journey end to end
- unit tests (Mockito): booking rules, vehicle verification toggle, location rules and rate limit, JWT provider, status machines

Without Docker, point the tests at a local database by replacing `@ServiceConnection` in
`AbstractIntegrationTest` with `spring.datasource.*` properties for an empty database (e.g. `rideshare_test`).

---

## 8. Security notes

- Passwords are hashed with BCrypt. Password hashes and refresh tokens never appear in responses, except that refresh tokens are returned on login and refresh only.
- Refresh tokens are opaque, stored only as SHA-256 hashes, rotated on every use, and reuse revokes every session. Logout, password change, password reset and deactivation revoke sessions.
- The JWT filter re-reads the account on every request, so deactivation takes effect immediately.
- User ids are never taken from the client for "who am I"; they always come from the `SecurityContext`.
- Login checks the account status only after the password matches. Forgot-password always answers 200 (no account enumeration).
- CORS and WebSocket origins come from configuration (no wildcard).
- Stack traces and internal errors are never returned (`server.error.include-*=never` plus the global handler).
- Admin endpoints are restricted both by URL rule and by `@PreAuthorize("hasRole('ADMIN')")`.

## 9. Possible extensions

Email/SMS delivery (implement `PasswordResetNotifier` / `NotificationChannel`), a Razorpay `PaymentGateway`,
Redis `LatestLocationStore` and a STOMP broker relay for horizontal scaling, nearby-pickup and passenger-count search filters.
