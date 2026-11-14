# Backend API Gaps

Features requested for the frontend that the backend does not (fully) support. The frontend will **not fake**
any of these. Each entry states the frontend behaviour now and the backend change that would close it.
No backend code has been changed.

Severity: **High** = blocks a requested workflow · **Medium** = degraded UX · **Low** = cosmetic / nice-to-have.

| # | Gap | Severity | Frontend behaviour | Backend change needed |
|---|---|---|---|---|
| G-01 | **No intermediate stops / waypoints** on rides | Medium | No stops UI on Publish/Details; route = source → destination | `ride_stops` table + `stops[]` in `RideRequest`/`RideResponse`; search matching on stops |
| G-02 | **Search has no `seats` filter** | Medium | Seats field kept on search form; passed to Ride Details / booking. Results show seat count and visually flag rides with fewer seats than requested (filter applies to the current page only — not presented as a server filter) | `minSeats` query param in `GET /rides/search` |
| G-03 | **Search requires an exact date** (`date` mandatory) | Low | Date is required on the search form; "browse all" uses `GET /rides` | Optional date / date range |
| G-04 | **No price/time/amenity filters** in search | Low | Filters dropdown offers only the supported **sort** options (departure time, price, seats) | Filter params |
| G-05 | **No amenities / ride preferences** fields | Low | Only the free-text `description` | Structured fields |
| G-06 | **BookingResponse has no payment status** | Medium | Booking Details loads `GET /payments/my-payments` and matches by `bookingId` client-side (paged; scans first pages) | Add `paymentStatus`/`latestPaymentId` to `BookingResponse` or `GET /bookings/{id}/payments` |
| G-07 | **Driver cannot discover a CASH `paymentId`** — `/payments/my-payments` is payer-only and no endpoint lists payments for a driver's rides/bookings, yet CASH confirmation requires the driver to send `paymentId` | **High** (cash flow) | Online (UPI/CARD/ONLINE simulated) payments fully supported. CASH: passenger can initiate; driver-side "Confirm cash received" **not offered** (documented, shown as "Pay the driver in cash") | `GET /payments/received` for drivers, or payment info on `BookingResponse` |
| G-08 | **No "reviews I wrote" / review-eligibility endpoint** | Low | Eligibility derived from `GET /rides/{id}/reviews` (filter by `reviewer.id === me`) + ride COMPLETED; backend 409/422 still handled | `GET /reviews/eligible` or `canReview` flags |
| G-09 | **No sorting of bookings by departure** (`my-bookings` sorts only by createdAt/updatedAt/status/seats) and **single-status filter** | Medium | "Upcoming" = PENDING + ACCEPTED (two requests merged), sorted client-side by `departureDate/Time` within loaded pages; dashboard "next booking" computed the same way | Multi-status filter + `ride.departureDate` sort |
| G-10 | **No email delivery** for password reset (verification codes now have SMTP delivery; the reset notifier still only logs) (token only logged on server console in dev; nothing in prod) | **High** (prod) | Forgot/Reset pages implemented against the real endpoints; UI says "If the email is registered, a link has been sent". Works end-to-end in dev via server log | Real `PasswordResetNotifier` (SMTP/provider) |
| G-11 | **No file upload** (avatar, registration document) | Medium | URL text inputs (`https://…`) as the API requires | Multipart upload endpoint + storage |
| G-12 | **Public profile, ratings & reviews require login** (`/users/{id}`, `/users/{id}/reviews`, `/rides/{id}/reviews`) | Low | Anonymous Ride Details shows driver name + `averageRating`/`totalReviews` from `DriverSummary`; full driver profile behind login | Permit these GETs publicly |
| G-13 | **No notification preferences/settings** | Low | Settings page omits notification settings | Preferences API |
| G-14 | **Admin cannot act on rides/bookings** (list only) | Low | Admin ride/booking pages are read-only lists with filters | Admin cancel endpoints |
| G-15 | **No admin action to delete reviews** — only hide/restore | — | Moderation = hide/restore with note (fully supported) | — |
| G-16 | **No geocoding / autocomplete** for locations; ride lat/lng optional | Medium | Text locations. Publish form can optionally set coordinates by clicking on a Leaflet map (sends real lat/lng). Ride map shows markers only when coordinates exist; no road route line (no routing data) | Store coords consistently; optional routing service |
| G-17 | **No reconnect-with-new-token for STOMP built in** — access token valid 15 min, STOMP authenticates only at CONNECT | Low | Client reconnects with a fresh token after refresh (`beforeConnect` hook) | — (works as designed) |
| G-18 | **Location `timestamp` is a `LocalDateTime` in platform TZ** (Asia/Kolkata) | Low | Frontend omits `timestamp`; server uses receive time | Accept `Instant` |
| G-19 | **No account reactivation by user** after self-deactivation | Low | Deactivate in Settings warns that only an admin can reactivate | — |
| G-20 | **Ride Details lists no passengers** for the driver | — | Uses `GET /bookings/received-requests?rideId=…&status=ACCEPTED` (supported) | — |
| G-21 | **Vehicle verification is required in prod** and there is no self-service path — only an admin can verify | Process | Publish form explains why unverified vehicles are disabled and links to My Vehicles; admin verification page implemented | — (business rule) |
| G-22 | **No chat / messaging** between driver and passenger | — | `contactPhone` shown once booking is ACCEPTED (backend-provided) | — |

## Not gaps (confirmed supported)

Unified account + mode preference · refresh-token rotation · forgot/reset/change password · self-deactivation ·
vehicle CRUD + verification · full ride lifecycle · booking request/accept/reject/cancel · simulated payments ·
mutual reviews + ratings · notifications (REST + live push) · live location (STOMP + REST fallback + history) ·
admin users/rides/bookings/vehicles/reviews/stats.

## Added with OTP / Google sign-in (2026-11-13)

| # | Note | Frontend behaviour |
|---|---|---|
| G-23 | Verification needs real providers: SMTP (`MAIL_*`) and Twilio (`TWILIO_*`). Without them registration returns 503 unless `OTP_LOG_CODES=true` (dev only: codes in server log). | 503 message shown on the register form |
| G-24 | Google sign-in requires `GOOGLE_CLIENT_ID` (backend) and `VITE_GOOGLE_CLIENT_ID` (frontend), with the frontend origin in "Authorized JavaScript origins". | Button hidden when unset |
| G-25 | Google-only accounts have no password; "change password" needs the current one, so they set one via Forgot password. | Settings explains this |
| G-26 | Phone change marks the number unverified but does not end existing sessions; verification is enforced at next password/Google sign-in. | Profile prompts to verify right after the change |
