/** Types mirror the backend DTOs (rideshare-backend). The server omits null fields, hence `?`. */
export type UUID = string;
/** UTC ISO instant, e.g. 2026-11-15T05:00:00Z */
export type Instant = string;
/** Platform-local date, e.g. 2026-11-15 */
export type LocalDate = string;
/** Platform-local time, e.g. 09:30:00 */
export type LocalTime = string;
/** Platform-local date-time without offset, e.g. 2026-11-15T14:30:00 */
export type LocalDateTime = string;
export type Money = number;

export type Role = 'USER' | 'ADMIN';
export type AccountStatus = 'ACTIVE' | 'DEACTIVATED';
export type ActiveMode = 'RIDER' | 'PASSENGER';
export type RideStatus = 'SCHEDULED' | 'ONGOING' | 'COMPLETED' | 'CANCELLED';
export type BookingStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED';
export type VehicleType = 'HATCHBACK' | 'SEDAN' | 'SUV' | 'MUV' | 'VAN' | 'OTHER';
export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
export type PaymentMethod = 'CASH' | 'UPI' | 'CARD' | 'ONLINE';
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';
export type NotificationType =
  | 'BOOKING_REQUEST'
  | 'BOOKING_ACCEPTED'
  | 'BOOKING_REJECTED'
  | 'BOOKING_CANCELLED'
  | 'RIDE_STARTED'
  | 'RIDE_COMPLETED'
  | 'RIDE_CANCELLED'
  | 'PAYMENT_SUCCESS'
  | 'PAYMENT_FAILED'
  | 'NEW_REVIEW'
  | 'SYSTEM';

export interface ApiFieldError {
  field: string;
  message: string;
}
export interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data?: T;
  errors?: ApiFieldError[];
  timestamp?: LocalDateTime;
}
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
export interface PageParams {
  page?: number;
  size?: number;
  sort?: string | string[];
}

export interface UserSummary {
  id: UUID;
  fullName: string;
  profilePicture?: string;
}
export interface UserProfile {
  id: UUID;
  fullName: string;
  email: string;
  phoneNumber: string;
  profilePicture?: string;
  activeMode: ActiveMode;
  accountStatus: AccountStatus;
  role: Role;
  averageRating?: number;
  totalReviews: number;
  emailVerified: boolean;
  phoneVerified: boolean;
  /** Account can sign in with Google. */
  googleLinked: boolean;
  /** False for Google-only accounts (no password yet). */
  passwordSet: boolean;
  createdAt: Instant;
  updatedAt: Instant;
}
export interface PublicUser {
  id: UUID;
  fullName: string;
  profilePicture?: string;
  averageRating?: number;
  totalReviews: number;
  memberSince: Instant;
}
export type VerificationChannel = 'EMAIL' | 'PHONE';
export interface ChannelStatus {
  channel: VerificationChannel;
  /** Masked, e.g. a***@example.com */
  destination: string;
  verified: boolean;
  codeSent: boolean;
  resendAvailableInSeconds: number;
}
export interface VerificationChallenge {
  verificationToken: string;
  expiresIn: number;
  channels: ChannelStatus[];
}
export interface GoogleSignup {
  signupToken: string;
  expiresIn: number;
  email: string;
  fullName: string;
  profilePicture?: string;
}
export type AuthFlowStatus = 'AUTHENTICATED' | 'VERIFICATION_REQUIRED' | 'PHONE_REQUIRED';
/** Result of register / verify / Google steps. Exactly one of auth, verification, googleSignup is set. */
export interface AuthFlowResponse {
  status: AuthFlowStatus;
  auth?: AuthResponse;
  verification?: VerificationChallenge;
  googleSignup?: GoogleSignup;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface Vehicle {
  id: UUID;
  ownerId: UUID;
  vehicleName: string;
  vehicleNumber: string;
  vehicleType: VehicleType;
  brand: string;
  model: string;
  color: string;
  seatingCapacity: number;
  registrationDocumentUrl?: string;
  verificationStatus: VerificationStatus;
  isVerified: boolean;
  rejectionReason?: string;
  createdAt: Instant;
  updatedAt: Instant;
}
export interface VehicleSummary {
  id: UUID;
  vehicleName: string;
  vehicleType: VehicleType;
  brand: string;
  model: string;
  color: string;
  isVerified: boolean;
}
export interface VehicleRequest {
  vehicleName: string;
  vehicleNumber: string;
  vehicleType: VehicleType;
  brand: string;
  model: string;
  color: string;
  seatingCapacity: number;
  registrationDocumentUrl?: string;
}

export interface DriverSummary {
  id: UUID;
  fullName: string;
  profilePicture?: string;
  averageRating?: number;
  totalReviews: number;
}
export interface Ride {
  id: UUID;
  source: string;
  destination: string;
  sourceLatitude?: number;
  sourceLongitude?: number;
  destinationLatitude?: number;
  destinationLongitude?: number;
  departureDate: LocalDate;
  departureTime: LocalTime;
  estimatedArrivalTime?: LocalDateTime;
  totalSeats: number;
  availableSeats: number;
  bookedSeats: number;
  pricePerSeat: Money;
  description?: string;
  rideStatus: RideStatus;
  driver: DriverSummary;
  vehicle: VehicleSummary;
  createdAt: Instant;
  updatedAt: Instant;
}
export interface RideRequest {
  vehicleId: UUID;
  source: string;
  destination: string;
  sourceLatitude?: number;
  sourceLongitude?: number;
  destinationLatitude?: number;
  destinationLongitude?: number;
  departureDate: LocalDate;
  departureTime: string;
  estimatedArrivalTime?: LocalDateTime;
  totalSeats: number;
  pricePerSeat: number;
  description?: string;
}

export interface BookingRideInfo {
  id: UUID;
  source: string;
  destination: string;
  departureDate: LocalDate;
  departureTime: LocalTime;
  rideStatus: RideStatus;
  pricePerSeat: Money;
}
export interface Booking {
  id: UUID;
  bookingReference: string;
  bookingStatus: BookingStatus;
  numberOfSeats: number;
  totalAmount: Money;
  ride: BookingRideInfo;
  passenger: UserSummary;
  driver: UserSummary;
  /** The other party's phone; only present once the booking is ACCEPTED. */
  contactPhone?: string;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface Payment {
  id: UUID;
  bookingId: UUID;
  bookingReference: string;
  payerId: UUID;
  amount: Money;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  transactionReference: string;
  provider: string;
  providerReference?: string;
  failureReason?: string;
  instructions?: string;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface Review {
  id: UUID;
  rideId: UUID;
  reviewer: UserSummary;
  reviewedUserId: UUID;
  rating: number;
  comment?: string;
  createdAt: Instant;
}
export interface UserRating {
  userId: UUID;
  averageRating?: number;
  totalReviews: number;
}
export interface UserReviews {
  rating: UserRating;
  reviews: PageResponse<Review>;
}

export interface AppNotification {
  id: UUID;
  title: string;
  message: string;
  notificationType: NotificationType;
  referenceId?: UUID;
  isRead: boolean;
  createdAt: Instant;
}

export interface LocationUpdate {
  rideId: UUID;
  latitude: number;
  longitude: number;
  timestamp: Instant;
  receivedAt: Instant;
}
export interface TrackingStatusMessage {
  rideId: UUID;
  rideStatus: RideStatus;
  trackingActive: boolean;
}

export interface AdminUser {
  id: UUID;
  fullName: string;
  email: string;
  phoneNumber: string;
  profilePicture?: string;
  role: Role;
  accountStatus: AccountStatus;
  activeMode: ActiveMode;
  emailVerified: boolean;
  phoneVerified: boolean;
  createdAt: Instant;
  updatedAt: Instant;
}
export interface AdminReview {
  id: UUID;
  rideId: UUID;
  reviewer: UserSummary;
  reviewedUser: UserSummary;
  rating: number;
  comment?: string;
  hidden: boolean;
  moderationNote?: string;
  createdAt: Instant;
}
export interface PlatformStats {
  totalUsers: number;
  activeUsers: number;
  deactivatedUsers: number;
  totalVehicles: number;
  pendingVehicleVerifications: number;
  verifiedVehicles: number;
  totalRides: number;
  ridesByStatus: Partial<Record<RideStatus, number>>;
  totalBookings: number;
  bookingsByStatus: Partial<Record<BookingStatus, number>>;
  totalReviews: number;
  totalSuccessfulPayments: Money;
}
