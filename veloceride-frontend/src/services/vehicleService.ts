import { api } from '@/api/apiClient';
import type { PageParams, PageResponse, Vehicle, VehicleRequest } from '@/types/dto';

export const vehicleService = {
  mine: (params: PageParams = {}) => api.get<PageResponse<Vehicle>>('/vehicles', { params }),
  get: (id: string) => api.get<Vehicle>(`/vehicles/${id}`),
  create: (body: VehicleRequest) => api.post<Vehicle>('/vehicles', body),
  update: (id: string, body: VehicleRequest) => api.put<Vehicle>(`/vehicles/${id}`, body),
  remove: (id: string) => api.delete(`/vehicles/${id}`),
};
