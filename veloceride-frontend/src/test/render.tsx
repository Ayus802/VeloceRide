import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router-dom';
import { queryClient } from '@/api/queryClient';
import { useAuthStore } from '@/store/authStore';
import { Toaster } from '@/components/common/Toaster';
import type { UserProfile } from '@/types/dto';
import { user as defaultUser } from '@/test/server';

/** Renders `element` at `path` inside a memory router; extra routes capture navigations. */
export function renderRoute(
  element: ReactElement,
  { path = '/', initialEntry = path, extraRoutes = [] }: { path?: string; initialEntry?: string; extraRoutes?: RouteObject[] } = {},
) {
  const router = createMemoryRouter(
    [{ path, element }, ...extraRoutes, { path: '*', element: <p>Other page</p> }],
    { initialEntries: [initialEntry] },
  );
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster />
    </QueryClientProvider>,
  );
  return { ...utils, router };
}

export function signIn(profile: Partial<UserProfile> = {}) {
  const tokens = { accessToken: 'access-1', refreshToken: 'refresh-1' };
  localStorage.setItem('veloceride.session', JSON.stringify(tokens));
  useAuthStore.setState({ status: 'authenticated', user: { ...defaultUser, ...profile }, ...tokens });
}
