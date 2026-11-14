import { beforeEach, describe, expect, it } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@/theme/ThemeProvider';
import { resolveTheme, THEME_STORAGE_KEY, useThemeStore } from '@/theme/useTheme';
import { ThemeMenu, ThemeSegmented } from '@/components/common/ThemeControls';
import { setSystemDark } from '@/test/matchMedia';

const html = () => document.documentElement;

function renderWithTheme(ui: React.ReactNode) {
  return render(
    <MemoryRouter>
      <ThemeProvider>{ui}</ThemeProvider>
    </MemoryRouter>,
  );
}

describe('theme', () => {
  beforeEach(() => {
    setSystemDark(false);
    localStorage.clear();
    html().removeAttribute('data-theme');
    useThemeStore.setState({ preference: 'system', resolved: resolveTheme('system') });
  });

  it('System follows the OS preference and reacts when it changes', () => {
    setSystemDark(true);
    useThemeStore.setState({ preference: 'system', resolved: resolveTheme('system') });
    renderWithTheme(<p>app</p>);
    expect(html().dataset.theme).toBe('dark');

    act(() => setSystemDark(false));
    expect(html().dataset.theme).toBe('light');
    expect(html().style.colorScheme).toBe('light');
  });

  it('Dark and Light ignore the OS preference', () => {
    renderWithTheme(<p>app</p>);
    act(() => useThemeStore.getState().setPreference('dark'));
    expect(html().dataset.theme).toBe('dark');
    act(() => setSystemDark(false));
    expect(html().dataset.theme).toBe('dark');

    act(() => useThemeStore.getState().setPreference('light'));
    act(() => setSystemDark(true));
    expect(html().dataset.theme).toBe('light');
  });

  it('persists the choice to localStorage', () => {
    renderWithTheme(<p>app</p>);
    act(() => useThemeStore.getState().setPreference('light'));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('the header menu switches theme from the UI', async () => {
    renderWithTheme(<ThemeMenu />);
    await userEvent.click(screen.getByRole('button', { name: /Theme/ }));
    await userEvent.click(screen.getByRole('menuitem', { name: /Dark/ }));
    expect(html().dataset.theme).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('the segmented control reflects and changes the preference', async () => {
    renderWithTheme(<ThemeSegmented />);
    expect(screen.getByRole('radio', { name: /System/ })).toHaveAttribute('aria-checked', 'true');
    await userEvent.click(screen.getByRole('radio', { name: /Light/ }));
    expect(screen.getByRole('radio', { name: /Light/ })).toHaveAttribute('aria-checked', 'true');
    expect(html().dataset.theme).toBe('light');
  });

  it('syncs a change made in another tab', () => {
    renderWithTheme(<p>app</p>);
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    act(() => {
      window.dispatchEvent(new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: 'dark' }));
    });
    expect(useThemeStore.getState().preference).toBe('dark');
    expect(html().dataset.theme).toBe('dark');
  });
});
