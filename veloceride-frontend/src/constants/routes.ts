export interface SearchParamsInput {
  from: string;
  to: string;
  date: string;
  seats: number;
}

export function searchUrl(v: SearchParamsInput) {
  const p = new URLSearchParams({ from: v.from, to: v.to, date: v.date, seats: String(v.seats) });
  return `/rides/search?${p}`;
}

/** Builds `/login?returnTo=…` for the current location. */
export function loginRedirect(pathname: string, search = '') {
  return `/login?returnTo=${encodeURIComponent(pathname + search)}`;
}

/** Only allow in-app relative paths as redirect targets. */
export function safeReturnTo(value: string | null | undefined, fallback = '/dashboard') {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return fallback;
  return value;
}
