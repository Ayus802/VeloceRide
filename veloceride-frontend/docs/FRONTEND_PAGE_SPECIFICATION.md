# Frontend Page Specification

Each page lists its route, access, the real endpoints it uses and its key UI decisions.
Status: ✅ fully supported · ◐ supported with documented limitations (see gap id) · ✖ not built.

## A. Public & auth

| # | Page | Route | Access | Endpoints | Status |
|---|---|---|---|---|---|
| 01 | Home | `/` | public | `GET /rides/search` (on submit → navigates to results) | ◐ G-02 |
| 02 | Register | `/register` | guest | `POST /auth/register` | ✅ |
| 03 | Login | `/login` | guest | `POST /auth/login` | ✅ |
| 04 | Forgot / Reset password | `/forgot-password`, `/reset-password?token=` | guest | `POST /auth/forgot-password`, `/auth/reset-password` | ◐ G-10 |

- **Home:** headline, search form (From, To, swap, Date, Seats), "Publish a ride" secondary link, 3-step How it works, minimal footer.
- **Register:** Full name, Email, Phone, Password, Confirm. Password rule hint under field. On success → tokens stored → `/dashboard`.
- **Login:** Email, Password, "Forgot password?" link. 401 → inline "Invalid email or password"; 403 → "Account is deactivated".

## B. Common authenticated

| # | Page | Route | Endpoints | Status |
|---|---|---|---|---|
| 05 | Dashboard | `/dashboard` | `GET /bookings/my-bookings?status=ACCEPTED`/`PENDING`, `GET /rides/my-rides?status=SCHEDULED&sort=departureDate,asc&sort=departureTime,asc&size=1`, `GET /bookings/received-requests?status=PENDING&size=5` | ◐ G-09 |
| 06 | My profile | `/profile` | `GET /users/me`, `GET /users/{me}/reviews?size=5` | ✅ |
| 07 | Edit profile | `/profile/edit` | `PUT /users/me` | ◐ G-11 (URL avatar) |
| 08 | Notifications | `/notifications` | `GET /notifications?read=`, `PATCH /{id}/read`, `PATCH /read-all`, `DELETE /{id}`, STOMP `/user/queue/notifications` | ✅ |
| 09 | Reviews & ratings | `/reviews` (mine), `/users/:userId` (public profile + reviews) | `GET /users/{id}/reviews`, `GET /users/{id}` | ✅ |
| 10 | Settings | `/settings` | `PATCH /users/me/password`, `PATCH /users/active-mode`, `PATCH /users/me/deactivate`, `POST /auth/logout {allDevices:true}` | ◐ G-13 |

- **Dashboard:** greeting; "Next trip" (soonest PENDING/ACCEPTED booking); "Next ride you drive"; "Requests waiting" (count + up to 3 rows with Accept/Reject); quick actions Find / Publish. No charts.
- **Settings sections:** Password · Default mode · Sessions ("Sign out of all devices") · Danger zone (deactivate, confirm modal).

## C. Passenger

| # | Page | Route | Endpoints | Status |
|---|---|---|---|---|
| 11 | Search rides | `/rides/search` (form on top of results) | — | ✅ |
| 12 | Search results | `/rides/search?from=&to=&date=&seats=&sort=` | `GET /rides/search` | ◐ G-02, G-04 |
| 13 | Ride details | `/rides/:rideId` | `GET /rides/{id}`; logged in: `GET /users/{driverId}`, `GET /rides/{id}/reviews` | ◐ G-01, G-12 |
| 14 | Booking confirmation | modal on Ride details | `POST /bookings` | ✅ |
| 15 | My bookings | `/bookings?tab=upcoming|completed|cancelled|rejected` | `GET /bookings/my-bookings?status=` | ◐ G-09 |
| 16 | Booking details | `/bookings/:bookingId` | `GET /bookings/{id}`, `PATCH /{id}/cancel`, payments (`initiate`, `confirm`, `my-payments`) | ◐ G-06, G-07 |
| 17 | Passenger live tracking | `/bookings/:bookingId/track` | `GET /bookings/{id}`, `GET /rides/{rideId}/location/latest`, STOMP location + status | ✅ |
| 18 | Review driver | modal from Booking details (COMPLETED) | `POST /reviews`, `GET /rides/{id}/reviews` | ◐ G-08 |

- **Results row:** time → time, From → To, driver avatar/name/★ rating, vehicle, seats left, price per seat; whole row links to details. Sort dropdown: Earliest departure · Lowest price · Most seats.
- **Booking modal:** seats stepper (1..min(availableSeats,20), prefilled from search), estimated total = seats × pricePerSeat labelled "Estimated"; authoritative `totalAmount` shown from response. Explains the request is PENDING until driver accepts. Unauthenticated → redirect to login with `returnTo`.
- **Booking tabs:** Upcoming = PENDING + ACCEPTED; Completed; Cancelled; Rejected.
- **Booking details:** status, reference, ride summary, driver (+ `contactPhone` when ACCEPTED), seats, total. Actions via ⋯ menu: Cancel (confirm modal). Pay section only when ACCEPTED and not already paid: method select → initiate → confirm (simulated). CASH shows "Pay the driver in cash" (G-07). Track button when ride ONGOING & booking ACCEPTED. Review button when COMPLETED & not yet reviewed.

## D. Rider / driver

| # | Page | Route | Endpoints | Status |
|---|---|---|---|---|
| 19 | Publish a ride | `/rides/new` | `GET /vehicles`, `POST /rides` | ◐ G-01, G-05, G-16 |
| 20 | My published rides | `/my-rides?tab=upcoming|ongoing|completed|cancelled` | `GET /rides/my-rides?status=` | ✅ |
| 21 | Ride management / edit | `/my-rides/:rideId` (manage), `/my-rides/:rideId/edit` | `GET /rides/{id}`, `PUT`, `DELETE`, `PATCH start/complete`, `GET received-requests?rideId=` | ✅ |
| 22 | Booking requests | `/requests` (all rides), also a section in ride management | `GET /bookings/received-requests?status=PENDING`, `PATCH accept/reject` | ✅ |
| 23 | Passenger management | section in `/my-rides/:rideId` | `GET /bookings/received-requests?rideId=&status=ACCEPTED` (+COMPLETED after ride) | ✅ |
| 24 | Driver live console | `/my-rides/:rideId/live` | `PATCH start/complete`, STOMP `/app/location/update`, status topic, REST fallback | ✅ |
| 25 | Review passengers | section in ride management when COMPLETED | `POST /reviews`, `GET /rides/{id}/reviews` | ◐ G-08 |

- **Publish form groups:** Route (From, To, optional "Pin on map" disclosure for coordinates) · When (Date, Time, optional Estimated arrival) · Vehicle & seats (vehicle select showing verification badge, unverified disabled with hint; seats ≤ capacity) · Price per seat · Optional notes (collapsed).
- **Edit:** same form, prefilled. When `bookedSeats > 0`, route/schedule/price/vehicle are read-only with an explanation (backend 409 rule); only seats & notes editable.
- **Manage page:** header with status and ⋯ menu (Edit, Cancel ride); primary action depends on status: Start (SCHEDULED) → Open live console (ONGOING) → Review passengers (COMPLETED). Sections: Requests (pending), Passengers (accepted).

## E. Vehicles

| # | Page | Route | Endpoints | Status |
|---|---|---|---|---|
| 26 | My vehicles | `/vehicles` | `GET /vehicles`, `DELETE /vehicles/{id}` | ✅ |
| 27 | Add vehicle | `/vehicles/new` | `POST /vehicles` | ◐ G-11 |
| 28 | Edit vehicle | `/vehicles/:id/edit` | `GET /vehicles/{id}`, `PUT /vehicles/{id}` | ✅ |

List shows name, brand model · color, type, number, capacity, verification status (+ rejection reason). Edit warns that changing number/document resets verification.

## F. Admin (role ADMIN; sidebar layout)

| # | Page | Route | Endpoints | Status |
|---|---|---|---|---|
| 29 | Admin dashboard | `/admin` | `GET /admin/stats` | ✅ |
| 30 | Users | `/admin/users`, `/admin/users/:id` | `GET /admin/users?q=`, `GET /{id}`, `PATCH /{id}/status` | ✅ |
| 31 | Rides | `/admin/rides` | `GET /admin/rides?status=` | ◐ G-14 read-only |
| 32 | Bookings | `/admin/bookings` | `GET /admin/bookings?status=` | ◐ G-14 read-only |
| 33 | Review moderation | `/admin/reviews` | `GET /admin/reviews?hidden=`, `PATCH /{id}/moderate` | ✅ |
| 33b | Vehicle verification *(extra, required by backend)* | `/admin/vehicles` | `GET /admin/vehicles?status=`, `PATCH verify/reject` | ✅ |

## Global

- **Header (auth):** logo · Dashboard · Find a ride · My trips (bookings / rides depending on mode) · bell with unread count · avatar dropdown (Profile, Reviews, Vehicles, Settings, Mode switch, Admin if ADMIN, Log out).
- **Mode switch:** `PATCH /users/active-mode`; changes "My trips" target and dashboard emphasis only.
- **404 page**, **403 state**, offline/error state.
