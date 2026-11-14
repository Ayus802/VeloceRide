import { useEffect, type ReactNode } from 'react';
import { applyTheme, DARK_QUERY, THEME_STORAGE_KEY, readStoredPreference, useThemeStore } from '@/theme/useTheme';

/**
 * Applies the theme to <html> and keeps it in sync with:
 * - the OS colour scheme (only while the preference is "system"),
 * - changes made in other tabs (storage event).
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    const { resolved, syncSystem, setPreference } = useThemeStore.getState();
    applyTheme(resolved);

    const mql = window.matchMedia?.(DARK_QUERY);
    const onSystemChange = () => syncSystem();
    mql?.addEventListener?.('change', onSystemChange);

    const onStorage = (e: StorageEvent) => {
      if (e.key === THEME_STORAGE_KEY) setPreference(readStoredPreference());
    };
    window.addEventListener('storage', onStorage);
    return () => {
      mql?.removeEventListener?.('change', onSystemChange);
      window.removeEventListener('storage', onStorage);
    };
  }, []);

  return children;
}
