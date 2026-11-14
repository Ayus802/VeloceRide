import { paymentService } from '@/services/paymentService';
import type { AppNotification } from '@/types/dto';

/** Where a notification leads, based on its type and referenceId (see docs/API_MAPPING.md §8). */
export async function notificationTarget(n: AppNotification): Promise<string | null> {
  const id = n.referenceId;
  if (!id) return null;
  switch (n.notificationType) {
    case 'BOOKING_REQUEST':
    case 'BOOKING_ACCEPTED':
    case 'BOOKING_REJECTED':
    case 'BOOKING_CANCELLED':
    case 'RIDE_CANCELLED':
      return `/bookings/${id}`;
    case 'RIDE_STARTED':
      return `/rides/${id}/track`;
    case 'RIDE_COMPLETED':
      return `/rides/${id}`;
    case 'NEW_REVIEW':
      return '/reviews';
    case 'PAYMENT_SUCCESS':
    case 'PAYMENT_FAILED':
    case 'SYSTEM': {
      const payment = await paymentService.get(id);
      return `/bookings/${payment.bookingId}`;
    }
    default:
      return null;
  }
}
