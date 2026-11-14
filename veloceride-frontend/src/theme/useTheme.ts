import { create } from 'zustand';

export type ThemePreference = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

/** Same key is read by the inline boot script in index.html to avoid a wrong-theme flash. */
export const THEME_STORAGE_KEY = 'veloceride.theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

export function readStoredPreference(): ThemePreference {
  try {
    const v = localStorage.getItem(THEME_STORAGE_KEY);
    return v === 'light' || v === 'dark' || v === 'system' ? v : 'system';
  } catch {
    return 'system';
  }
}

export function systemTheme(): ResolvedTheme {
  return typeof window !== 'undefined' && window.matchMedia?.(DARK_QUERY).matches ? 'dark' : 'light';
}

export function resolveTheme(pref: ThemePreference): ResolvedTheme {
  return pref === 'system' ? systemTheme() : pref;
}

const META_COLORS: Record<ResolvedTheme, string> = { dark: '#0B1220', light: '#F7F9FC' };

/** Writes the resolved theme to <html> (data-theme + color-scheme) and the browser UI colour. */
export function applyTheme(theme: ResolvedTheme) {
  const root = document.documentElement;
  if (root.dataset.theme === theme) return;
  root.setAttribute('data-theme-switching', '');
  root.dataset.theme = theme;
  root.style.colorScheme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', META_COLORS[theme]);
  // Re-enable transitions after the browser has painted the new colours.
  requestAnimationFrame(() => requestAnimationFrame(() => root.removeAttribute('data-theme-switching')));
}

interface ThemeState {
  preference: ThemePreference;
  resolved: ResolvedTheme;
  setPreference: (p: ThemePreference) => void;
  /** Called when the OS colour scheme changes. */
  syncSystem: () => void;
}

export const useThemeStore = create<ThemeState>((set, get) => {
  const preference = readStoredPreference();
  return {
    preference,
    resolved: resolveTheme(preference),
    setPreference: (p) => {
      try {
        localStorage.setItem(THEME_STORAGE_KEY, p);
      } catch {
        /* storage unavailable: preference lasts for this page only */
      }
      const resolved = resolveTheme(p);
      applyTheme(resolved);
      set({ preference: p, resolved });
    },
    syncSystem: () => {
      if (get().preference !== 'system') return;
      const resolved = systemTheme();
      applyTheme(resolved);
      set({ resolved });
    },
  };
});

/**
 * Theme preference hook. Stored locally only: the backend has no user-preferences endpoint
 * (its only preference, activeMode, is a different setting), so nothing is invented server-side.
 */
export function useTheme() {
  const preference = useThemeStore((s) => s.preference);
  const resolved = useThemeStore((s) => s.resolved);
  const setPreference = useThemeStore((s) => s.setPreference);
  return { preference, resolved, setPreference };
}

export { DARK_QUERY };
