import '@testing-library/jest-dom/vitest';
import { installMatchMedia } from '@/test/matchMedia';

installMatchMedia();
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import { server } from '@/test/server';
import { queryClient } from '@/api/queryClient';
import { useAuthStore } from '@/store/authStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  queryClient.clear();
  localStorage.clear();
  sessionStorage.clear();
  useAuthStore.setState({ status: 'anonymous', user: null, accessToken: null, refreshToken: null });
});
afterAll(() => server.close());
