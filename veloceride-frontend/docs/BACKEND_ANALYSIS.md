# Backend Analysis — VeloceRide (rideshare-backend)

Source of truth: `../rideshare-backend` (Spring Boot 3.4.5, Java 17, PostgreSQL via Flyway, ~7.1k LOC).
Analysis date: 2026-11-13. Nothing in the backend was modified for this analysis.

## 1. Project structure

Package-by-feature under `com.rideshare`:

| Module | Contents |
|---|---|
| `auth` | Register, login, refresh (rotating opaque tokens), logout, forgot/reset password |
| `user` | Own profile, public profile, change password, active-mode preference, self-deactivation |
| `vehicle` | Owner CRUD, admin verification (PENDING → VERIFIED / REJECTED) |
| `ride` | Publish, update, cancel, start, complete, public browse/search/details |
| `booking` | Passenger requests seats; driver accepts/rejects; passenger cancels |
| `payment` | Initiate/confirm behind a `PaymentGateway` abstraction (only `SIMULATED` provider exists) |
| `review` | Post-ride mutual reviews (driver ↔ passenger), user rating aggregate |
| `notification` | Stored notifications from domain events, also pushed via STOMP |
| `location` | Live driver location (STOMP + REST fallback), latest + history |
| `admin` | Users, rides, bookings, vehicles verification, review moderation, stats |
| `common` | `ApiResponse` envelope, `PageResponse`, exceptions, enums, validation patterns |
| `config` / `security` | JWT, CORS, WebSocket, OpenAPI, Spring Security |

Modules communicate through Spring application events (`BookingEvent`, `RideEvent`, `PaymentEvent`,
`ReviewEvent`), consumed by notifications, payments (refund on cancel) and location (stop tracking).

## 2. Data model (Flyway V1–V10)

```
users 1─* vehicles (soft-deleted via `deleted`)
users 1─* rides (driver)        vehicles 1─* rides
rides 1─* bookings              users 1─* bookings (passenger)
bookings 1─* payments           users 1─* payments (payer)
rides 1─* reviews (reviewer → reviewed_user)
users 1─* notifications         rides 1─* ride_locations
users 1─* refresh_tokens        users 1─* password_reset_tokens
```

All ids are UUIDs. There is **no** table for stops/waypoints, amenities, preferences, messages/chat,
profile bio, or notification settings.

## 3. Response conventions (critical for the API client)

Every JSON response (except `204 No Content`) uses this envelope:

```ts
{ success: boolean; message: string; data?: T; errors?: { field: string; message: string }[]; timestamp: string }
```

- Success: `data` present, `errors` omitted.
- Error: `success:false`, `errors` is `[]` or a list of field errors (400 validation).
- Cross-field `PasswordsMatch` errors are reported on field `confirmPassword`.
- Lists use `PageResponse<T>`: `{ content, page, size, totalElements, totalPages, first, last }`.
- Paging params: Spring `page` (0-based), `size` (clamped to 1..100), `sort=field,asc|desc`.
  **Sorting on a non-whitelisted field returns 400** — whitelists are listed in `API_MAPPING.md`.
- `DELETE /api/vehicles/{id}` and `DELETE /api/notifications/{id}` return **204 with empty body**.

### Date/time types

| Field kind | JSON form | Meaning |
|---|---|---|
| `Instant` (`createdAt`, `updatedAt`, `memberSince`, location `timestamp`/`receivedAt`) | `2026-11-15T05:00:00Z` | UTC |
| `LocalDate` (`departureDate`) | `2026-11-15` | Platform local date (`APP_TIME_ZONE`, default `Asia/Kolkata`) |
| `LocalTime` (`departureTime`) | `09:30:00` (request accepts `H:mm` or `H:mm:ss`) | Platform local time |
| `LocalDateTime` (`estimatedArrivalTime`, envelope `timestamp`) | `2026-11-15T14:30:00` | Platform local, no offset |
| `BigDecimal` (prices, amounts) | number, e.g. `650.00` | Currency is INR (hard-coded in payments) |

The frontend must display ride dates/times as given (they are already local to the platform) and must not
convert them through the browser time zone.

### HTTP status semantics

| Status | Source | Frontend handling |
|---|---|---|
| 400 | Bean validation (`errors[]`), `BadRequestException`, malformed body, bad sort | Map `errors[].field` onto form fields; else toast `message` |
| 401 | Missing/invalid/expired access token; wrong credentials on login; invalid refresh token | Refresh once (not for `/auth/*`), else logout |
| 403 | Not owner/participant, admin-only, deactivated account at login | Show "not allowed" state; never retry |
| 404 | Resource missing or not visible to caller | Not-found state |
| 409 | `ConflictException`, `DuplicateResourceException`, DB unique constraints, optimistic locking | Show `message`; refetch |
| 422 | `BusinessRuleException` (seat counts, own ride, verification required, start window…) | Show `message` inline |
| 429 | Location rate limit | Silently drop / back off |
| 500 | Unexpected | Generic error state |

## 4. Authentication & authorization

- **Login identity:** email + password. No username.
- **Register:** `fullName`, `email`, `phoneNumber`, `password`, `confirmPassword`. Returns tokens → user is signed in immediately.
- **Access token:** JWT (HS), subject = user id, claims `type=access`, `role`. Lifetime `JWT_ACCESS_EXPIRATION` (default 15 min); `expiresIn` (seconds) is returned.
- **Refresh token:** opaque, single use, rotated on every `/auth/refresh`. Lifetime 7 days.
  **Reusing a revoked refresh token revokes all sessions of the user (theft detection; no grace period).**
  ⇒ Frontend must single-flight refreshes, including across browser tabs (e.g. Web Locks API + shared storage).
- **Logout:** `POST /auth/logout { refreshToken, allDevices }` — public, idempotent.
- **Password reset:** token is delivered only by `LoggingPasswordResetNotifier` (logged to server console in dev; *no email provider*). Link format: `${FRONTEND_URL}/reset-password?token=<raw>`.
- **Roles:** `USER`, `ADMIN`. Admin bootstrapped from `ADMIN_EMAIL`/`ADMIN_PASSWORD`. `/api/admin/**` requires ADMIN.
- **Ownership** (driver of ride, passenger of booking, owner of vehicle) is enforced in services, never by `activeMode`.
- **Deactivated accounts:** login → 403; existing tokens → 401 "Account is deactivated".
- **CORS:** origins from `FRONTEND_URL` (currently `http://localhost:5173`), credentials allowed, headers `Authorization, Content-Type, Accept, X-Requested-With`.

### Public vs protected

Public: `POST /api/auth/{register,login,refresh,logout,forgot-password,reset-password}`,
`GET /api/rides`, `GET /api/rides/search`, `GET /api/rides/{rideId}`, Swagger, `/actuator/health`, WebSocket handshake.
Everything else requires a bearer token — **including** `GET /api/users/{id}`, `/users/{id}/reviews`,
`/users/{id}/rating`, `/rides/{id}/reviews`.

## 5. Unified account & modes

One account can both publish and book. `activeMode` (`PASSENGER` | `RIDER`, default `PASSENGER`) is stored
server-side via `PATCH /api/users/active-mode` and returned on the profile. It is a UI preference only and
grants nothing.

## 6. Status machines

**RideStatus:** `SCHEDULED → ONGOING → COMPLETED`; `SCHEDULED → CANCELLED`.
**BookingStatus:** `PENDING → ACCEPTED | REJECTED | CANCELLED`; `ACCEPTED → CANCELLED | COMPLETED`.
**PaymentStatus:** `PENDING → SUCCESS | FAILED`; `SUCCESS → REFUNDED` (on cancellation).
**VerificationStatus:** `PENDING → VERIFIED | REJECTED`; editing number/document resets to `PENDING`.

## 7. Key business rules (enforced by backend)

Rides
- Publisher becomes the driver; vehicle must be own (403) and VERIFIED when `VEHICLE_VERIFICATION_REQUIRED=true` (422) — **true in the prod profile currently in use**, so a new user cannot publish until an admin verifies their vehicle.
- Source ≠ destination; departure must be in the future; arrival after departure; `totalSeats` ≤ vehicle capacity (422).
- Update only while SCHEDULED; once any bookings exist, route/schedule/price/vehicle are locked (409); seats cannot drop below booked (422). `PUT` is a full replace.
- Start allowed from `RIDE_START_WINDOW` before departure (default 2h; 30 days in dev). Start auto-rejects pending requests.
- Complete: accepted bookings → COMPLETED; reviews unlocked.
- Cancel (`DELETE`): all active bookings cancelled, payments refunded, passengers notified.
- Search: case-insensitive *contains* match on source/destination, exact `date` (required, not in the past), SCHEDULED, future, `availableSeats > 0`.

Bookings
- Cannot book own ride (422); ride must be SCHEDULED and not departed (409); seats ≤ available (422); one active booking per ride per passenger (409).
- Seats are allocated only on ACCEPT (422 if insufficient). `totalAmount` computed server-side.
- Passenger may cancel while PENDING/ACCEPTED and ride not started.
- `contactPhone` (other party's phone) only populated once ACCEPTED.

Payments (simulated)
- Passenger initiates for an ACCEPTED booking with `CASH | UPI | CARD | ONLINE`.
- Online: payer calls `/confirm` (server decides; `providerPaymentId: "SIMULATE_FAILURE"` simulates decline).
- CASH: **driver** must call `/confirm` with the `paymentId` (see gap G-07).

Reviews
- Only after ride COMPLETED; reviewer must be a participant; passengers may only review the driver;
  driver may review passengers who were on the ride; no self-review; one review per (ride, reviewer, reviewed).
- Hidden (moderated) reviews are excluded from ratings.

Location
- Only the ride's driver publishes, only while ONGOING, coordinates validated, rate limit 1 per `LOCATION_MIN_UPDATE_INTERVAL` (2 s).
- Viewing/subscribing: driver or passenger with ACCEPTED booking (subscribe allowed while SCHEDULED or ONGOING).

## 8. Validation rules (mirror in Zod, do not tighten)

| Field | Rule |
|---|---|
| `fullName` | required, 2–100 chars |
| `email` | required, valid email, ≤255 |
| `phoneNumber` | `^\+?[1-9]\d{7,14}$` |
| `password` / `newPassword` | `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,64}$` |
| `confirmPassword` | must equal password |
| `profilePicture`, `registrationDocumentUrl` | optional, ≤1000, `^https?://` URL |
| `vehicleName` | required, ≤100 |
| `vehicleNumber` | `^[A-Za-z0-9 -]{4,20}$` (stored upper-case, spaces/dashes removed; unique) |
| `vehicleType` | `HATCHBACK | SEDAN | SUV | MUV | VAN | OTHER` |
| `brand`, `model` / `color` | required, ≤50 / ≤30 |
| `seatingCapacity`, `totalSeats` | 1–20 |
| `source`, `destination` | required, ≤200 |
| lat / lng | [-90,90] / [-180,180], optional on rides |
| `departureDate` | today or later; date+time must be future |
| `pricePerSeat` | ≥0, max 8 integer digits, 2 decimals |
| `description` (ride) | ≤1000 |
| `numberOfSeats` (booking) | 1–20 |
| `rating` | 1–5; `comment` ≤1000 |
| admin `reason` ≤500 required; moderation `note` ≤500 optional |

## 9. WebSocket

STOMP over `/ws` (native) and `/ws-sockjs` (SockJS). Fully documented in `WEBSOCKET_MAPPING.md`.

## 10. Environment

- Local backend: `http://localhost:8080`; Swagger UI `/swagger-ui.html` (→ `/swagger-ui/index.html`), spec `/v3/api-docs` (enabled by `SWAGGER_ENABLED=true`; paths confirmed in `SecurityConfig` and `application.yml`).
- Database: Neon PostgreSQL (prod profile, configured in `rideshare-backend/.env`).
- Uploads: **none**. Profile pictures and registration documents are URLs only.
