import { api } from '@/api/apiClient';
import type {
  AccountStatus,
  AdminReview,
  AdminUser,
  Booking,
  BookingStatus,
  PageParams,
  PageResponse,
  PlatformStats,
  Ride,
  RideStatus,
  Vehicle,
  VerificationStatus,
} from '@/types/dto';

export const adminService = {
  stats: () => api.get<PlatformStats>('/admin/stats'),
  users: (params: PageParams & { q?: string } = {}) => api.get<PageResponse<AdminUser>>('/admin/users', { params }),
  setUserStatus: (id: string, accountStatus: AccountStatus) =>
    api.patch<AdminUser>(`/admin/users/${id}/status`, { accountStatus }),
  rides: (params: PageParams & { status?: RideStatus } = {}) => api.get<PageResponse<Ride>>('/admin/rides', { params }),
  bookings: (params: PageParams & { status?: BookingStatus } = {}) =>
    api.get<PageResponse<Booking>>('/admin/bookings', { params }),
  vehicles: (params: PageParams & { status?: VerificationStatus } = {}) =>
    api.get<PageResponse<Vehicle>>('/admin/vehicles', { params }),
  verifyVehicle: (id: string) => api.patch<Vehicle>(`/admin/vehicles/${id}/verify`),
  rejectVehicle: (id: string, reason: string) => api.patch<Vehicle>(`/admin/vehicles/${id}/reject`, { reason }),
  reviews: (params: PageParams & { hidden?: boolean } = {}) =>
    api.get<PageResponse<AdminReview>>('/admin/reviews', { params }),
  moderateReview: (id: string, hidden: boolean, note?: string) =>
    api.patch<AdminReview>(`/admin/reviews/${id}/moderate`, { hidden, note: note || undefined }),
};
