import { api } from '@/api/apiClient';
import type { AuthFlowResponse, AuthResponse, VerificationChannel } from '@/types/dto';
import type { LoginValues, RegisterValues } from '@/utils/validation';

export const authService = {
  login: (body: LoginValues) => api.post<AuthResponse>('/auth/login', body),
  /** Creates an unverified account; codes are sent by email and SMS. */
  register: (body: RegisterValues) => api.post<AuthFlowResponse>('/auth/register', body),
  confirmCode: (verificationToken: string, channel: VerificationChannel, code: string) =>
    api.post<AuthFlowResponse>('/auth/verification/confirm', { verificationToken, channel, code }),
  resendCode: (verificationToken: string, channel: VerificationChannel) =>
    api.post<AuthFlowResponse>('/auth/verification/resend', { verificationToken, channel }),
  /** idToken = the `credential` from Google Identity Services. */
  google: (idToken: string) => api.post<AuthFlowResponse>('/auth/google', { idToken }),
  completeGoogleSignup: (signupToken: string, phoneNumber: string) =>
    api.post<AuthFlowResponse>('/auth/google/complete', { signupToken, phoneNumber }),
  logout: (refreshToken: string, allDevices = false) => api.post<void>('/auth/logout', { refreshToken, allDevices }),
  forgotPassword: (email: string) => api.post<void>('/auth/forgot-password', { email }),
  resetPassword: (body: { token: string; newPassword: string; confirmPassword: string }) =>
    api.post<void>('/auth/reset-password', body),
};
