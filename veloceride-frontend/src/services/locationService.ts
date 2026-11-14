import { api } from '@/api/apiClient';
import type { LocationUpdate } from '@/types/dto';

export const locationService = {
  latest: (rideId: string) => api.get<LocationUpdate>(`/rides/${rideId}/location/latest`),
  /** REST fallback used only when the STOMP connection is down. Timestamp omitted: server time is used. */
  publish: (rideId: string, latitude: number, longitude: number) =>
    api.post<LocationUpdate>(`/rides/${rideId}/location`, { latitude, longitude }),
};
