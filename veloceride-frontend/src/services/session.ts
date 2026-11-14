import { queryClient } from '@/api/queryClient';
import { TOKEN_STORAGE_KEY, tokenStorage } from '@/api/tokenStorage';
import { useAuthStore } from '@/store/authStore';
import { authService } from '@/services/authService';
import { userService } from '@/services/userService';
import { disconnectRealtime } from '@/services/realtime/stompClient';
import type { AuthResponse } from '@/types/dto';
import { isAppError } from '@/utils/errors';

/** Restores a stored session on page load by loading the profile (the API client refreshes if needed). */
export async function bootstrapSession() {
  const store = useAuthStore.getState();
  if (!store.refreshToken) {
    store.markAnonymous();
    return;
  }
  try {
    store.setUser(await userService.me());
  } catch (err) {
    // Network/5xx: keep tokens and retry on next load, but treat as signed out for now.
    if (isAppError(err) && err.status >= 400 && err.status < 500) store.clearSession();
    else useAuthStore.getState().markAnonymous();
  }
}

export function startSession(auth: AuthResponse) {
  queryClient.clear();
  useAuthStore.getState().setSession(auth);
}

/** Revokes the refresh token server-side (best effort) and clears local state everywhere. */
export async function endSession(allDevices = false) {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (refreshToken) {
    try {
      await authService.logout(refreshToken, allDevices);
    } catch {
      /* logout is idempotent; local sign-out still proceeds */
    }
  }
  clearLocalSession();
}

export function clearLocalSession() {
  void disconnectRealtime();
  useAuthStore.getState().clearSession();
  queryClient.clear();
}

/** Keeps tabs in sync: sign-out or token rotation in one tab is reflected in the others. */
export function listenForCrossTabChanges() {
  const onStorage = (e: StorageEvent) => {
    if (e.key !== TOKEN_STORAGE_KEY) return;
    const tokens = tokenStorage.read();
    const store = useAuthStore.getState();
    if (!tokens) {
      if (store.status === 'authenticated') clearLocalSession();
      return;
    }
    if (tokens.accessToken !== store.accessToken) {
      useAuthStore.setState({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
      if (store.status !== 'authenticated') void bootstrapSession();
    }
  };
  window.addEventListener('storage', onStorage);
  return () => window.removeEventListener('storage', onStorage);
}
