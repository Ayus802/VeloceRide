import { api } from '@/api/apiClient';
import type { LocalDate, PageParams, PageResponse, Review, Ride, RideRequest, RideStatus } from '@/types/dto';

export interface RideSearchParams extends PageParams {
  source: string;
  destination: string;
  date: LocalDate;
}

export const rideService = {
  search: (params: RideSearchParams) => api.get<PageResponse<Ride>>('/rides/search', { params }),
  upcoming: (params: PageParams = {}) => api.get<PageResponse<Ride>>('/rides', { params }),
  get: (id: string) => api.get<Ride>(`/rides/${id}`),
  mine: (params: PageParams & { status?: RideStatus } = {}) =>
    api.get<PageResponse<Ride>>('/rides/my-rides', { params }),
  publish: (body: RideRequest) => api.post<Ride>('/rides', body),
  update: (id: string, body: RideRequest) => api.put<Ride>(`/rides/${id}`, body),
  cancel: (id: string) => api.delete<Ride>(`/rides/${id}`),
  start: (id: string) => api.patch<Ride>(`/rides/${id}/start`),
  complete: (id: string) => api.patch<Ride>(`/rides/${id}/complete`),
  reviews: (id: string) => api.get<Review[]>(`/rides/${id}/reviews`),
};
