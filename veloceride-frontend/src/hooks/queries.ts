import { useQueries } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { bookingService } from '@/services/bookingService';
import { MAX_PAGE_SIZE } from '@/constants/config';
import type { Booking } from '@/types/dto';
import { departureKey } from '@/utils/format';

/**
 * Upcoming bookings = PENDING + ACCEPTED. The backend filters one status at a time and cannot sort
 * by departure (BACKEND_API_GAPS G-09), so both lists are loaded (100 each) and merged client-side.
 */
export function useUpcomingBookings() {
  const results = useQueries({
    queries: (['ACCEPTED', 'PENDING'] as const).map((status) => ({
      queryKey: qk.myBookings({ status, size: MAX_PAGE_SIZE }),
      queryFn: () => bookingService.mine({ status, size: MAX_PAGE_SIZE }),
    })),
  });
  const isPending = results.some((r) => r.isPending);
  const error = results.find((r) => r.isError)?.error ?? null;
  const bookings: Booking[] = results
    .flatMap((r) => r.data?.content ?? [])
    .filter((b) => b.ride.rideStatus === 'SCHEDULED' || b.ride.rideStatus === 'ONGOING')
    .sort((a, b) =>
      departureKey(a.ride.departureDate, a.ride.departureTime).localeCompare(departureKey(b.ride.departureDate, b.ride.departureTime)),
    );
  return { bookings, isPending, error, refetch: () => results.forEach((r) => r.refetch()) };
}
