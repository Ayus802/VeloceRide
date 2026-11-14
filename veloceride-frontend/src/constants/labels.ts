import type {
  BookingStatus,
  PaymentMethod,
  PaymentStatus,
  RideStatus,
  VehicleType,
  VerificationStatus,
} from '@/types/dto';

export const RIDE_STATUS_LABEL: Record<RideStatus, string> = {
  SCHEDULED: 'Scheduled',
  ONGOING: 'On the road',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export const BOOKING_STATUS_LABEL: Record<BookingStatus, string> = {
  PENDING: 'Awaiting driver',
  ACCEPTED: 'Confirmed',
  REJECTED: 'Declined',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
};

export const VERIFICATION_LABEL: Record<VerificationStatus, string> = {
  PENDING: 'Pending verification',
  VERIFIED: 'Verified',
  REJECTED: 'Rejected',
};

export const VEHICLE_TYPE_LABEL: Record<VehicleType, string> = {
  HATCHBACK: 'Hatchback',
  SEDAN: 'Sedan',
  SUV: 'SUV',
  MUV: 'MUV',
  VAN: 'Van',
  OTHER: 'Other',
};
export const VEHICLE_TYPES = Object.keys(VEHICLE_TYPE_LABEL) as VehicleType[];

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  UPI: 'UPI',
  CARD: 'Card',
  ONLINE: 'Net banking',
  CASH: 'Cash',
};

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Payment pending',
  SUCCESS: 'Paid',
  FAILED: 'Payment failed',
  REFUNDED: 'Refunded',
};
