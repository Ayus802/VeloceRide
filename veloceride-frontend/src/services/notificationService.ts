import { api } from '@/api/apiClient';
import type { AppNotification, PageParams, PageResponse } from '@/types/dto';

export const notificationService = {
  list: (params: PageParams & { read?: boolean } = {}) =>
    api.get<PageResponse<AppNotification>>('/notifications', { params }),
  unreadCount: () => api.get<{ unreadCount: number }>('/notifications/unread-count'),
  markRead: (id: string) => api.patch<AppNotification>(`/notifications/${id}/read`),
  markAllRead: () => api.patch<{ unreadCount: number }>('/notifications/read-all'),
  remove: (id: string) => api.delete(`/notifications/${id}`),
};
