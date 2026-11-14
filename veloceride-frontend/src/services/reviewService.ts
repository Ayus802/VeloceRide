import { api } from '@/api/apiClient';
import type { Review } from '@/types/dto';

export interface CreateReviewBody {
  rideId: string;
  reviewedUserId: string;
  rating: number;
  comment?: string;
}

export const reviewService = {
  create: (body: CreateReviewBody) => api.post<Review>('/reviews', body),
};
