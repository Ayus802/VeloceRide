/**
 * Persists the bearer/refresh token pair. localStorage is used (instead of memory only) so that
 * reloads keep the session and all tabs share one rotating refresh token — the backend revokes
 * every session if an already-rotated refresh token is presented again.
 */
export const TOKEN_STORAGE_KEY = 'veloceride.session';

export interface StoredTokens {
  accessToken: string;
  refreshToken: string;
}

export const tokenStorage = {
  read(): StoredTokens | null {
    try {
      const raw = localStorage.getItem(TOKEN_STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<StoredTokens>;
      return parsed.accessToken && parsed.refreshToken
        ? { accessToken: parsed.accessToken, refreshToken: parsed.refreshToken }
        : null;
    } catch {
      return null;
    }
  },
  write(tokens: StoredTokens) {
    try {
      localStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(tokens));
    } catch {
      /* storage unavailable (private mode): session lives in memory only */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    } catch {
      /* ignore */
    }
  },
};

/** Seconds until the JWT expires (negative when expired); null when it cannot be decoded. */
export function secondsUntilExpiry(jwt: string, now = Date.now()): number | null {
  try {
    const payload = jwt.split('.')[1];
    if (!payload) return null;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number };
    return typeof json.exp === 'number' ? json.exp - Math.floor(now / 1000) : null;
  } catch {
    return null;
  }
}
