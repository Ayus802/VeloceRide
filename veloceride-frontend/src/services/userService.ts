import { api } from '@/api/apiClient';
import type { ActiveMode, ChannelStatus, VerificationChannel, PageParams, PublicUser, UserProfile, UserRating, UserReviews } from '@/types/dto';
import type { ChangePasswordValues } from '@/utils/validation';

export const userService = {
  me: () => api.get<UserProfile>('/users/me'),
  updateMe: (body: { fullName: string; phoneNumber: string; profilePicture?: string }) =>
    api.put<UserProfile>('/users/me', body),
  changePassword: (body: ChangePasswordValues) => api.patch<void>('/users/me/password', body),
  switchMode: (activeMode: ActiveMode) => api.patch<UserProfile>('/users/active-mode', { activeMode }),
  deactivate: () => api.patch<void>('/users/me/deactivate'),
  sendVerification: (channel: VerificationChannel) => api.post<ChannelStatus>(`/users/me/verification/${channel}/send`),
  confirmVerification: (channel: VerificationChannel, code: string) =>
    api.post<UserProfile>(`/users/me/verification/${channel}/confirm`, { code }),
  publicProfile: (userId: string) => api.get<PublicUser>(`/users/${userId}`),
  reviews: (userId: string, params: PageParams = {}) => api.get<UserReviews>(`/users/${userId}/reviews`, { params }),
  rating: (userId: string) => api.get<UserRating>(`/users/${userId}/rating`),
};
