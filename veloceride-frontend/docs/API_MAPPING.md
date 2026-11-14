# API Mapping

Base URL: `${VITE_API_BASE_URL}` (e.g. `http://localhost:8080`). All paths below start with `/api`.
Responses are wrapped in `ApiResponse<T>` (see `BACKEND_ANALYSIS.md §3`); the **Response** column shows `T`.
`Page<X>` = `PageResponse<X>`. Auth: 🌐 public · 🔒 bearer token · 🛡 ADMIN.

## TypeScript types (derived from backend DTOs)

```ts
type UUID = string;
type Instant = string;        // UTC ISO, e.g. 2026-11-15T05:00:00Z
type LocalDate = string;      // 2026-11-15 (platform time zone)
type LocalTime = string;      // 09:30:00
type LocalDateTime = string;  // 2026-11-15T14:30:00 (platform time zone, no offset)
type Money = number;          // BigDecimal, INR

type Role = 'USER' | 'ADMIN';
type AccountStatus = 'ACTIVE' | 'DEACTIVATED';
type ActiveMode = 'RIDER' | 'PASSENGER';
type RideStatus = 'SCHEDULED' | 'ONGOING' | 'COMPLETED' | 'CANCELLED';
type BookingStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED';
type VehicleType = 'HATCHBACK' | 'SEDAN' | 'SUV' | 'MUV' | 'VAN' | 'OTHER';
type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
type PaymentMethod = 'CASH' | 'UPI' | 'CARD' | 'ONLINE';
type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';
type NotificationType = 'BOOKING_REQUEST' | 'BOOKING_ACCEPTED' | 'BOOKING_REJECTED' | 'BOOKING_CANCELLED'
  | 'RIDE_STARTED' | 'RIDE_COMPLETED' | 'RIDE_CANCELLED' | 'PAYMENT_SUCCESS' | 'PAYMENT_FAILED' | 'NEW_REVIEW' | 'SYSTEM';

interface ApiResponse<T> { success: boolean; message: string; data?: T; errors?: ApiError[]; timestamp: LocalDateTime }
interface ApiError { field: string; message: string }
interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }

interface UserSummary { id: UUID; fullName: string; profilePicture: string | null }
interface UserProfileResponse { id: UUID; fullName: string; email: string; phoneNumber: string; profilePicture: string | null;
  activeMode: ActiveMode; accountStatus: AccountStatus; role: Role; averageRating: number | null; totalReviews: number;
  createdAt: Instant; updatedAt: Instant }
interface PublicUserResponse { id: UUID; fullName: string; profilePicture: string | null; averageRating: number | null;
  totalReviews: number; memberSince: Instant }
interface AuthResponse { accessToken: string; refreshToken: string; tokenType: 'Bearer'; expiresIn: number; user: UserProfileResponse }

interface VehicleResponse { id: UUID; ownerId: UUID; vehicleName: string; vehicleNumber: string; vehicleType: VehicleType;
  brand: string; model: string; color: string; seatingCapacity: number; registrationDocumentUrl: string | null;
  verificationStatus: VerificationStatus; isVerified: boolean; rejectionReason: string | null; createdAt: Instant; updatedAt: Instant }
interface VehicleSummary { id: UUID; vehicleName: string; vehicleType: VehicleType; brand: string; model: string; color: string; isVerified: boolean }

interface DriverSummary { id: UUID; fullName: string; profilePicture: string | null; averageRating: number | null; totalReviews: number }
interface RideResponse { id: UUID; source: string; destination: string; sourceLatitude: number | null; sourceLongitude: number | null;
  destinationLatitude: number | null; destinationLongitude: number | null; departureDate: LocalDate; departureTime: LocalTime;
  estimatedArrivalTime: LocalDateTime | null; totalSeats: number; availableSeats: number; bookedSeats: number; pricePerSeat: Money;
  description: string | null; rideStatus: RideStatus; driver: DriverSummary; vehicle: VehicleSummary; createdAt: Instant; updatedAt: Instant }

interface BookingRideInfo { id: UUID; source: string; destination: string; departureDate: LocalDate; departureTime: LocalTime;
  rideStatus: RideStatus; pricePerSeat: Money }
interface BookingResponse { id: UUID; bookingReference: string; bookingStatus: BookingStatus; numberOfSeats: number; totalAmount: Money;
  ride: BookingRideInfo; passenger: UserSummary; driver: UserSummary; contactPhone: string | null; createdAt: Instant; updatedAt: Instant }

interface PaymentResponse { id: UUID; bookingId: UUID; bookingReference: string; payerId: UUID; amount: Money; paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus; transactionReference: string; provider: string; providerReference: string | null;
  failureReason: string | null; instructions: string | null; createdAt: Instant; updatedAt: Instant }

interface ReviewResponse { id: UUID; rideId: UUID; reviewer: UserSummary; reviewedUserId: UUID; rating: number; comment: string | null; createdAt: Instant }
interface UserRatingResponse { userId: UUID; averageRating: number | null; totalReviews: number }
interface UserReviewsResponse { rating: UserRatingResponse; reviews: PageResponse<ReviewResponse> }

interface NotificationResponse { id: UUID; title: string; message: string; notificationType: NotificationType;
  referenceId: UUID | null; isRead: boolean; createdAt: Instant }

interface LocationResponse { rideId: UUID; latitude: number; longitude: number; timestamp: Instant; receivedAt: Instant }

interface AdminUserResponse { id: UUID; fullName: string; email: string; phoneNumber: string; profilePicture: string | null; role: Role;
  accountStatus: AccountStatus; activeMode: ActiveMode; createdAt: Instant; updatedAt: Instant }
interface AdminReviewResponse { id: UUID; rideId: UUID; reviewer: UserSummary; reviewedUser: UserSummary; rating: number;
  comment: string | null; hidden: boolean; moderationNote: string | null; createdAt: Instant }
interface PlatformStatsResponse { totalUsers: number; activeUsers: number; deactivatedUsers: number; totalVehicles: number;
  pendingVehicleVerifications: number; verifiedVehicles: number; totalRides: number; ridesByStatus: Partial<Record<RideStatus, number>>;
  totalBookings: number; bookingsByStatus: Partial<Record<BookingStatus, number>>; totalReviews: number; totalSuccessfulPayments: Money }
```

> Nullability: Jackson is configured with `default-property-inclusion: non_null`, so **null fields are omitted**
> from JSON. Treat every nullable field above as `T | undefined` at runtime (use `?:` in the real types).

---

## Account verification & Google (added 2026-11-13)

```ts
type VerificationChannel = 'EMAIL' | 'PHONE';
interface ChannelStatus { channel: VerificationChannel; destination: string /* masked */; verified: boolean; codeSent: boolean; resendAvailableInSeconds: number }
interface VerificationChallenge { verificationToken: string; expiresIn: number; channels: ChannelStatus[] }
interface GoogleSignup { signupToken: string; expiresIn: number; email: string; fullName: string; profilePicture?: string }
interface AuthFlowResponse { status: 'AUTHENTICATED' | 'VERIFICATION_REQUIRED' | 'PHONE_REQUIRED'; auth?: AuthResponse; verification?: VerificationChallenge; googleSignup?: GoogleSignup }
// UserProfileResponse gains: emailVerified, phoneVerified, googleLinked, passwordSet. AdminUserResponse: emailVerified, phoneVerified.
```

Flows: register → codes to email + SMS → confirm both → tokens. Google → (existing account linked by verified email |
new user adds phone → SMS code) → tokens. Accounts created before verification existed were marked verified by migration V11.

## 1. Auth — `/api/auth` 🌐

| Method | Path | Request | Response | Errors / notes |
|---|---|---|---|---|
| POST | `/auth/register` | `{fullName, email, phoneNumber, password, confirmPassword}` | `AuthFlowResponse` (201, `VERIFICATION_REQUIRED`) | 400 validation; 409 email/phone exists; 503 codes can't be delivered |
| POST | `/auth/login` | `{email, password}` | `AuthResponse` | 401 "Invalid email or password"; 403 deactivated; **403 with `data: AuthFlowResponse` (VERIFICATION_REQUIRED)** when email/phone unverified |
| POST | `/auth/verification/confirm` | `{verificationToken, channel: EMAIL\|PHONE, code}` | `AuthFlowResponse` | 400 wrong/expired code (5 attempts per code); 401 expired verification token |
| POST | `/auth/verification/resend` | `{verificationToken, channel}` | `AuthFlowResponse` | 429 cooldown (60 s) or > 5 codes/hour |
| POST | `/auth/google` | `{idToken}` (Google Identity Services credential) | `AuthFlowResponse` (`AUTHENTICATED` / `VERIFICATION_REQUIRED` / `PHONE_REQUIRED`) | 401 invalid token; 400 Google email unverified; 503 not configured |
| POST | `/auth/google/complete` | `{signupToken, phoneNumber}` | `AuthFlowResponse` (201, `VERIFICATION_REQUIRED` for phone) | 401 expired signup token; 409 phone/email taken |
| POST | `/auth/refresh` | `{refreshToken}` | `AuthResponse` | 401 invalid/expired/revoked; reuse ⇒ all sessions revoked |
| POST | `/auth/logout` | `{refreshToken, allDevices}` | `null` | idempotent |
| POST | `/auth/forgot-password` | `{email}` | `null` | always 200 |
| POST | `/auth/reset-password` | `{token, newPassword, confirmPassword}` | `null` | 400 invalid/expired token; signs out all sessions |

## 2. Users — `/api/users` 🔒

| Method | Path | Request | Response | Errors / notes |
|---|---|---|---|---|
| GET | `/users/me` | — | `UserProfileResponse` | |
| PUT | `/users/me` | `{fullName, phoneNumber, profilePicture?}` | `UserProfileResponse` | full replace; 409 phone taken; email not editable; **changing the phone sets `phoneVerified=false`** |
| PATCH | `/users/me/password` | `{currentPassword, newPassword, confirmPassword}` | `null` | 400 wrong current / same as old; signs out other sessions |
| PATCH | `/users/active-mode` | `{activeMode}` | `UserProfileResponse` | preference only |
| PATCH | `/users/me/deactivate` | — | `null` | revokes all sessions; only admin can reactivate |
| POST | `/users/me/verification/{EMAIL\|PHONE}/send` | — | `ChannelStatus` | 400 already verified; 429 cooldown |
| POST | `/users/me/verification/{EMAIL\|PHONE}/confirm` | `{code}` | `UserProfileResponse` | 400 wrong/expired code |
| GET | `/users/{userId}` | — | `PublicUserResponse` | 404 |

## 3. Vehicles — `/api/vehicles` 🔒

| Method | Path | Request | Response | Errors / notes |
|---|---|---|---|---|
| POST | `/vehicles` | `VehicleRequest` | `VehicleResponse` (201) | starts PENDING; 409 duplicate number |
| GET | `/vehicles` | `page,size,sort` | `Page<VehicleResponse>` | sort: `createdAt, vehicleName, verificationStatus` |
| GET | `/vehicles/{id}` | — | `VehicleResponse` | owner or admin (403) |
| PUT | `/vehicles/{id}` | `VehicleRequest` | `VehicleResponse` | owner only; changing number/doc resets to PENDING |
| DELETE | `/vehicles/{id}` | — | **204 empty** | 409 if scheduled/ongoing rides |

`VehicleRequest = {vehicleName, vehicleNumber, vehicleType, brand, model, color, seatingCapacity, registrationDocumentUrl?}`

## 4. Rides — `/api/rides`

| Method | Path | Auth | Request | Response | Errors / notes |
|---|---|---|---|---|---|
| POST | `/rides` | 🔒 | `RideRequest` | `RideResponse` (201) | 403 not own vehicle; 422 unverified vehicle / seats > capacity; 400 past date, same src/dst |
| GET | `/rides` | 🌐 | `page,size,sort` | `Page<RideResponse>` | upcoming SCHEDULED rides with seats |
| GET | `/rides/search` | 🌐 | `source*`, `destination*`, `date*` (yyyy-MM-dd), `page,size,sort` | `Page<RideResponse>` | contains-match; 400 past date / missing param; sort: `departureTime, pricePerSeat, availableSeats, createdAt` |
| GET | `/rides/my-rides` | 🔒 | `status?`, paging | `Page<RideResponse>` | rides I drive |
| GET | `/rides/{id}` | 🌐 | — | `RideResponse` | any status |
| PUT | `/rides/{id}` | 🔒 driver | `RideRequest` | `RideResponse` | 409 not SCHEDULED or locked by bookings; 422 seats < booked |
| DELETE | `/rides/{id}` | 🔒 driver | — | `RideResponse` | cancels; 409 invalid transition |
| PATCH | `/rides/{id}/start` | 🔒 driver | — | `RideResponse` | 422 too early; 409 not SCHEDULED |
| PATCH | `/rides/{id}/complete` | 🔒 driver | — | `RideResponse` | 409 not ONGOING |

List sort whitelist (`/rides`, `/my-rides`, admin): `departureDate, departureTime, pricePerSeat, availableSeats, createdAt, rideStatus`.

`RideRequest = {vehicleId, source, destination, sourceLatitude?, sourceLongitude?, destinationLatitude?, destinationLongitude?,
departureDate, departureTime ("HH:mm"), estimatedArrivalTime? ("yyyy-MM-ddTHH:mm:ss"), totalSeats, pricePerSeat, description?}`

## 5. Bookings — `/api/bookings` 🔒

| Method | Path | Request | Response | Errors / notes |
|---|---|---|---|---|
| POST | `/bookings` | `{rideId, numberOfSeats}` | `BookingResponse` (201, PENDING) | 422 own ride / too many seats; 409 ride closed/departed or active booking exists |
| GET | `/bookings/my-bookings` | `status?`, paging | `Page<BookingResponse>` | as passenger |
| GET | `/bookings/received-requests` | `rideId?`, `status?`, paging | `Page<BookingResponse>` | as driver |
| GET | `/bookings/{id}` | — | `BookingResponse` | passenger, driver or admin (403) |
| PATCH | `/bookings/{id}/accept` | — | `BookingResponse` | driver; 422 seats; 409 not PENDING / ride not SCHEDULED |
| PATCH | `/bookings/{id}/reject` | — | `BookingResponse` | driver; 409 not PENDING |
| PATCH | `/bookings/{id}/cancel` | — | `BookingResponse` | passenger; 409 already final / ride started |

Sort whitelist: `createdAt, updatedAt, bookingStatus, numberOfSeats` (default `createdAt,desc`).

## 6. Payments — `/api/payments` 🔒 (simulated provider)

| Method | Path | Request | Response | Errors / notes |
|---|---|---|---|---|
| POST | `/payments/initiate` | `{bookingId, paymentMethod}` | `PaymentResponse` (201, PENDING, with `instructions`) | 403 not your booking; 409 not ACCEPTED / already paid |
| POST | `/payments/confirm` | `{paymentId, providerPaymentId?, providerSignature?}` | `PaymentResponse` | CASH → driver only; others → payer only; 409 not PENDING |
| GET | `/payments/my-payments` | paging | `Page<PaymentResponse>` | as payer; sort `createdAt, amount, paymentStatus` |
| GET | `/payments/{id}` | — | `PaymentResponse` | payer, driver or admin |

## 7. Reviews 🔒

| Method | Path | Request | Response | Errors / notes |
|---|---|---|---|---|
| POST | `/reviews` | `{rideId, reviewedUserId, rating, comment?}` | `ReviewResponse` (201) | 403 not participant; 422 ride not completed / self / passenger→passenger; 409 duplicate |
| GET | `/users/{userId}/reviews` | paging (sort `createdAt, rating`) | `UserReviewsResponse` | excludes hidden |
| GET | `/users/{userId}/rating` | — | `UserRatingResponse` | |
| GET | `/rides/{rideId}/reviews` | — | `ReviewResponse[]` (not paged) | used to detect "already reviewed" |

## 8. Notifications — `/api/notifications` 🔒

| Method | Path | Request | Response | Notes |
|---|---|---|---|---|
| GET | `/notifications` | `read?`, paging (sort `createdAt`) | `Page<NotificationResponse>` | newest first |
| GET | `/notifications/unread-count` | — | `{unreadCount}` | |
| PATCH | `/notifications/{id}/read` | — | `NotificationResponse` | 404 if not yours |
| PATCH | `/notifications/read-all` | — | `{unreadCount: 0}` | |
| DELETE | `/notifications/{id}` | — | **204 empty** | |

`referenceId` by type (for deep links):

| Type | referenceId | Recipient | Link to |
|---|---|---|---|
| BOOKING_REQUEST, BOOKING_CANCELLED | bookingId | driver | Booking requests / booking detail |
| BOOKING_ACCEPTED, BOOKING_REJECTED, RIDE_CANCELLED | bookingId | passenger | Booking detail |
| RIDE_STARTED | rideId | passengers | Live tracking |
| RIDE_COMPLETED | rideId | passengers & driver | Review page |
| PAYMENT_SUCCESS, PAYMENT_FAILED, SYSTEM (refund) | paymentId | payer (+driver on success) | `GET /payments/{id}` → booking |
| NEW_REVIEW | reviewId | reviewed user | My reviews |

## 9. Location 🔒 (see `WEBSOCKET_MAPPING.md`)

| Method | Path | Request | Response | Notes |
|---|---|---|---|---|
| POST | `/rides/{rideId}/location` | `{latitude, longitude, timestamp?}` | `LocationResponse` | driver, ONGOING, 429 rate limit — REST fallback |
| GET | `/rides/{rideId}/location/latest` | — | `LocationResponse` | driver/accepted passenger; 409 not ONGOING; 404 none yet |
| GET | `/rides/{rideId}/location/history` | paging (sort `recordedAt`) | `Page<LocationResponse>` | driver/accepted or completed passengers |

## 10. Admin — `/api/admin` 🛡

| Method | Path | Request | Response | Notes |
|---|---|---|---|---|
| GET | `/admin/users` | `q?`, paging (sort `createdAt, fullName, email, accountStatus`) | `Page<AdminUserResponse>` | q matches name/email/phone |
| GET | `/admin/users/{id}` | — | `AdminUserResponse` | |
| PATCH | `/admin/users/{id}/status` | `{accountStatus}` | `AdminUserResponse` | 422 on own account; deactivation revokes sessions |
| GET | `/admin/rides` | `status?`, paging | `Page<RideResponse>` | read-only |
| GET | `/admin/bookings` | `status?`, paging | `Page<BookingResponse>` | read-only |
| GET | `/admin/vehicles` | `status` (default PENDING), paging | `Page<VehicleResponse>` | |
| PATCH | `/admin/vehicles/{id}/verify` | — | `VehicleResponse` | |
| PATCH | `/admin/vehicles/{id}/reject` | `{reason}` | `VehicleResponse` | |
| GET | `/admin/reviews` | `hidden?`, paging | `Page<AdminReviewResponse>` | |
| PATCH | `/admin/reviews/{id}/moderate` | `{hidden, note?}` | `AdminReviewResponse` | |
| GET | `/admin/stats` | — | `PlatformStatsResponse` | |
