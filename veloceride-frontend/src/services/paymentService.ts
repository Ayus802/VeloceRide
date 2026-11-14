import { api } from '@/api/apiClient';
import type { PageParams, PageResponse, Payment, PaymentMethod } from '@/types/dto';

export const paymentService = {
  initiate: (bookingId: string, paymentMethod: PaymentMethod) =>
    api.post<Payment>('/payments/initiate', { bookingId, paymentMethod }),
  /** The server verifies with the provider; there is no client-side "success" flag. */
  confirm: (paymentId: string) => api.post<Payment>('/payments/confirm', { paymentId }),
  mine: (params: PageParams = {}) => api.get<PageResponse<Payment>>('/payments/my-payments', { params }),
  get: (id: string) => api.get<Payment>(`/payments/${id}`),
};
