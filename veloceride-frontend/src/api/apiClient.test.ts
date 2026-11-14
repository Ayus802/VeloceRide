import { describe, expect, it } from 'vitest';
import { api } from '@/api/apiClient';
import { useAuthStore } from '@/store/authStore';
import { API, auth, fail, http, HttpResponse, ok, server, user } from '@/test/server';
import { signIn } from '@/test/render';
import { AppError } from '@/utils/errors';

describe('apiClient', () => {
  it('unwraps the ApiResponse envelope and sends the bearer token', async () => {
    signIn();
    let authHeader: string | null = null;
    server.use(
      http.get(`${API}/users/me`, ({ request }) => {
        authHeader = request.headers.get('authorization');
        return ok(user);
      }),
    );
    await expect(api.get('/users/me')).resolves.toEqual(user);
    expect(authHeader).toBe('Bearer access-1');
  });

  it('serialises repeated sort params the way Spring expects', async () => {
    let query = '';
    server.use(
      http.get(`${API}/rides`, ({ request }) => {
        query = new URL(request.url).search;
        return ok({ content: [] });
      }),
    );
    await api.get('/rides', { params: { sort: ['departureDate,asc', 'departureTime,asc'] } });
    expect(decodeURIComponent(query)).toBe('?sort=departureDate,asc&sort=departureTime,asc');
  });

  it('normalises errors with field messages', async () => {
    server.use(http.post(`${API}/auth/register`, () => fail(400, 'Validation failed', [{ field: 'email', message: 'must be a well-formed email address' }])));
    const err = (await api.post('/auth/register', {}).catch((e) => e)) as AppError;
    expect(err).toBeInstanceOf(AppError);
    expect(err.status).toBe(400);
    expect(err.fieldErrors).toEqual({ email: 'must be a well-formed email address' });
  });

  it('returns undefined for 204 responses', async () => {
    signIn();
    server.use(http.delete(`${API}/vehicles/v-1`, () => new HttpResponse(null, { status: 204 })));
    await expect(api.delete('/vehicles/v-1')).resolves.toBeUndefined();
  });

  it('refreshes once on 401 for concurrent requests, then retries them', async () => {
    signIn();
    let refreshCalls = 0;
    server.use(
      http.post(`${API}/auth/refresh`, async ({ request }) => {
        refreshCalls++;
        expect(await request.json()).toEqual({ refreshToken: 'refresh-1' });
        return ok({ ...auth, accessToken: 'access-2', refreshToken: 'refresh-2' });
      }),
      http.get(`${API}/users/me`, ({ request }) =>
        request.headers.get('authorization') === 'Bearer access-2' ? ok(user) : fail(401, 'Invalid or expired token'),
      ),
    );
    const results = await Promise.all([api.get('/users/me'), api.get('/users/me'), api.get('/users/me')]);
    expect(results).toEqual([user, user, user]);
    expect(refreshCalls).toBe(1);
    expect(useAuthStore.getState().refreshToken).toBe('refresh-2');
    expect(JSON.parse(localStorage.getItem('veloceride.session')!)).toEqual({ accessToken: 'access-2', refreshToken: 'refresh-2' });
  });

  it('clears the session when the refresh token is rejected', async () => {
    signIn();
    server.use(
      http.post(`${API}/auth/refresh`, () => fail(401, 'Refresh token has been revoked')),
      http.get(`${API}/users/me`, () => fail(401, 'Invalid or expired token')),
    );
    const err = (await api.get('/users/me').catch((e) => e)) as AppError;
    expect(err.status).toBe(401);
    expect(useAuthStore.getState().status).toBe('anonymous');
    expect(localStorage.getItem('veloceride.session')).toBeNull();
  });

  it('reuses tokens rotated by another tab instead of presenting a stale refresh token', async () => {
    signIn();
    // Another tab already rotated: storage holds a newer pair than this tab's memory.
    localStorage.setItem('veloceride.session', JSON.stringify({ accessToken: 'access-9', refreshToken: 'refresh-9' }));
    let refreshCalls = 0;
    server.use(
      http.post(`${API}/auth/refresh`, () => {
        refreshCalls++;
        return fail(401, 'Refresh token has been revoked');
      }),
      http.get(`${API}/users/me`, ({ request }) =>
        request.headers.get('authorization') === 'Bearer access-9' ? ok(user) : fail(401, 'Invalid or expired token'),
      ),
    );
    await expect(api.get('/users/me')).resolves.toEqual(user);
    expect(refreshCalls).toBe(0);
  });

  it('does not try to refresh on 403', async () => {
    signIn();
    let refreshCalls = 0;
    server.use(
      http.post(`${API}/auth/refresh`, () => {
        refreshCalls++;
        return ok(auth);
      }),
      http.get(`${API}/admin/stats`, () => fail(403, 'You do not have permission to access this resource')),
    );
    const err = (await api.get('/admin/stats').catch((e) => e)) as AppError;
    expect(err.status).toBe(403);
    expect(refreshCalls).toBe(0);
  });
});
