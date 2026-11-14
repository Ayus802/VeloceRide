import { create } from 'zustand';
import { tokenStorage, type StoredTokens } from '@/api/tokenStorage';
import type { AuthResponse, UserProfile } from '@/types/dto';

export type AuthStatus = 'booting' | 'authenticated' | 'anonymous';

interface AuthState {
  status: AuthStatus;
  user: UserProfile | null;
  accessToken: string | null;
  refreshToken: string | null;
  setSession: (auth: AuthResponse) => void;
  setTokens: (tokens: StoredTokens) => void;
  setUser: (user: UserProfile) => void;
  clearSession: () => void;
  markAnonymous: () => void;
}

const initial = tokenStorage.read();

export const useAuthStore = create<AuthState>((set) => ({
  status: initial ? 'booting' : 'anonymous',
  user: null,
  accessToken: initial?.accessToken ?? null,
  refreshToken: initial?.refreshToken ?? null,

  setSession: (auth) => {
    tokenStorage.write({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
    set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user, status: 'authenticated' });
  },
  setTokens: (tokens) => {
    tokenStorage.write(tokens);
    set({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
  },
  setUser: (user) => set({ user, status: 'authenticated' }),
  clearSession: () => {
    tokenStorage.clear();
    set({ accessToken: null, refreshToken: null, user: null, status: 'anonymous' });
  },
  markAnonymous: () => set({ status: 'anonymous' }),
}));

export const selectIsAdmin = (s: AuthState) => s.user?.role === 'ADMIN';
