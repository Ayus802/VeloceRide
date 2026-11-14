import { api } from '@/api/apiClient';
import type { Booking, BookingStatus, PageParams, PageResponse } from '@/types/dto';

export const bookingService = {
  create: (rideId: string, numberOfSeats: number) => api.post<Booking>('/bookings', { rideId, numberOfSeats }),
  mine: (params: PageParams & { status?: BookingStatus } = {}) =>
    api.get<PageResponse<Booking>>('/bookings/my-bookings', { params }),
  received: (params: PageParams & { rideId?: string; status?: BookingStatus } = {}) =>
    api.get<PageResponse<Booking>>('/bookings/received-requests', { params }),
  get: (id: string) => api.get<Booking>(`/bookings/${id}`),
  accept: (id: string) => api.patch<Booking>(`/bookings/${id}/accept`),
  reject: (id: string) => api.patch<Booking>(`/bookings/${id}/reject`),
  cancel: (id: string) => api.patch<Booking>(`/bookings/${id}/cancel`),
};
