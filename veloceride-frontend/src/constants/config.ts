const trimSlash = (v: string) => v.replace(/\/+$/, '');

export const API_BASE_URL = trimSlash(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080');

export const WS_URL = import.meta.env.VITE_WS_URL || `${API_BASE_URL.replace(/^http/, 'ws')}/ws`;

/** Ride dates/times from the backend are local to this zone (APP_TIME_ZONE on the server). */
export const PLATFORM_TIME_ZONE = import.meta.env.VITE_PLATFORM_TIME_ZONE || 'Asia/Kolkata';

/** Google Identity Services OAuth client id; Google sign-in is hidden when unset. */
export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

/** Location updates older than this are shown as stale. */
export const LOCATION_STALE_MS = 30_000;
/** Backend accepts one update per ride every 2 s; we send at most one every 4 s. */
export const LOCATION_PUBLISH_INTERVAL_MS = 4_000;
/** Backend clamps page size to 100. */
export const MAX_PAGE_SIZE = 100;
