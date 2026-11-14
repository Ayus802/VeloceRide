import { QueryClient } from '@tanstack/react-query';
import { isAppError } from '@/utils/errors';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      // Retry only transient failures (network / 5xx); 4xx answers are authoritative.
      retry: (count, err) => count < 2 && (!isAppError(err) || err.status === 0 || err.status >= 500),
    },
    mutations: { retry: false },
  },
});

/** Query keys in one place so mutations can invalidate precisely. */
export const qk = {
  me: ['me'] as const,
  publicUser: (id: string) => ['users', id] as const,
  userReviews: (id: string, params?: object) => ['users', id, 'reviews', params ?? {}] as const,
  rides: ['rides'] as const,
  ride: (id: string) => ['rides', 'detail', id] as const,
  rideSearch: (params: object) => ['rides', 'search', params] as const,
  myRides: (params: object) => ['rides', 'mine', params] as const,
  rideReviews: (id: string) => ['rides', 'detail', id, 'reviews'] as const,
  bookings: ['bookings'] as const,
  booking: (id: string) => ['bookings', 'detail', id] as const,
  myBookings: (params: object) => ['bookings', 'mine', params] as const,
  received: (params: object) => ['bookings', 'received', params] as const,
  payments: ['payments'] as const,
  vehicles: ['vehicles'] as const,
  vehicle: (id: string) => ['vehicles', id] as const,
  notifications: ['notifications'] as const,
  notificationList: (params: object) => ['notifications', 'list', params] as const,
  unreadCount: ['notifications', 'unread'] as const,
  admin: ['admin'] as const,
};
