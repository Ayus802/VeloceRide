import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '@/constants/config';
import { useAuthStore } from '@/store/authStore';
import { tokenStorage } from '@/api/tokenStorage';
import type { ApiEnvelope, AuthResponse } from '@/types/dto';
import { AppError } from '@/utils/errors';

/**
 * Single Axios instance for the backend.
 * - Adds the bearer token.
 * - Unwraps the ApiResponse envelope (callers get `data`).
 * - Normalises failures into AppError.
 * - On 401 refreshes the session once (single-flight, cross-tab locked) and retries.
 */
export const http = axios.create({
  baseURL: `${API_BASE_URL}/api`,
  headers: { Accept: 'application/json' },
  timeout: 30_000,
  // Spring expects repeated params: sort=a,asc&sort=b,desc (not sort[]=…)
  paramsSerializer: { indexes: null },
});

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean; _sentToken?: string | null };

http.interceptors.request.use((config: RetriableConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  config._sentToken = token;
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const config = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const isAuthCall = config?.url?.startsWith('/auth/');

    if (status === 401 && config && !config._retried && !isAuthCall && config._sentToken) {
      config._retried = true;
      const newToken = await refreshSession(config._sentToken);
      if (newToken) {
        config.headers.set('Authorization', `Bearer ${newToken}`);
        config._sentToken = newToken;
        return http.request(config);
      }
    }
    return Promise.reject(toAppError(error));
  },
);

export function toAppError(error: unknown): AppError {
  if (error instanceof AppError) return error;
  if (axios.isAxiosError(error)) {
    const res = error.response;
    if (!res) return new AppError(0, 'Network error');
    const body = res.data as ApiEnvelope<unknown> | undefined;
    const fieldErrors: Record<string, string> = {};
    for (const e of body?.errors ?? []) {
      if (e.field && !(e.field in fieldErrors)) fieldErrors[e.field] = e.message;
    }
    return new AppError(res.status, body?.message || defaultMessage(res.status), fieldErrors, body?.data);
  }
  return new AppError(0, error instanceof Error ? error.message : 'Unexpected error');
}

function defaultMessage(status: number) {
  if (status === 401) return 'Your session has expired. Please log in again.';
  if (status === 403) return 'You do not have permission to do that.';
  if (status === 404) return 'Not found.';
  if (status >= 500) return 'The server had a problem. Please try again.';
  return 'Request failed.';
}

// ---------------------------------------------------------------- refresh (single flight)

let inFlight: Promise<string | null> | null = null;

async function withCrossTabLock<T>(fn: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  if (locks?.request) {
    return locks.request('veloceride-token-refresh', fn) as Promise<T>;
  }
  return fn();
}

/**
 * Exchanges the refresh token for a new pair. `staleAccessToken` is the token that was rejected:
 * if another tab already rotated the session, its fresh token is reused instead of presenting
 * the (now revoked) refresh token again — which the backend would treat as token theft.
 */
export function refreshSession(staleAccessToken?: string | null): Promise<string | null> {
  if (!inFlight) {
    inFlight = withCrossTabLock(async () => {
      const store = useAuthStore.getState();
      const stored = tokenStorage.read();
      if (!stored) {
        store.clearSession();
        return null;
      }
      if (staleAccessToken && stored.accessToken !== staleAccessToken) {
        store.setTokens(stored); // another tab refreshed already
        return stored.accessToken;
      }
      try {
        const res = await axios.post<ApiEnvelope<AuthResponse>>(
          `${API_BASE_URL}/api/auth/refresh`,
          { refreshToken: stored.refreshToken },
          { timeout: 30_000 },
        );
        const auth = res.data.data!;
        store.setSession(auth);
        return auth.accessToken;
      } catch (err) {
        const status = axios.isAxiosError(err) ? err.response?.status : undefined;
        if (status && status < 500) {
          store.clearSession(); // refresh token invalid/expired/revoked
        }
        return null;
      }
    }).finally(() => {
      inFlight = null;
    });
  }
  return inFlight;
}

// ---------------------------------------------------------------- typed helpers (envelope unwrapped)

async function unwrap<T>(p: Promise<{ data: ApiEnvelope<T> | '' }>): Promise<T> {
  const res = await p;
  return (res.data === '' ? undefined : res.data?.data) as T;
}

export const api = {
  get: <T>(url: string, config?: AxiosRequestConfig) => unwrap<T>(http.get(url, config)),
  post: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) => unwrap<T>(http.post(url, body, config)),
  put: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) => unwrap<T>(http.put(url, body, config)),
  patch: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) => unwrap<T>(http.patch(url, body, config)),
  delete: <T = void>(url: string, config?: AxiosRequestConfig) => unwrap<T>(http.delete(url, config)),
};
