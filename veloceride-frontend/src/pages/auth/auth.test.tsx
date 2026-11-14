import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginPage from '@/pages/auth/LoginPage';
import RegisterPage from '@/pages/auth/RegisterPage';
import { ProtectedRoute } from '@/routes/guards';
import { useAuthStore } from '@/store/authStore';
import { API, auth, fail, http, ok, server } from '@/test/server';
import { renderRoute, signIn } from '@/test/render';

describe('authentication flow', () => {
  it('logs in, stores the session and returns to the requested page', async () => {
    server.use(http.post(`${API}/auth/login`, () => ok(auth)));
    const { router } = renderRoute(<LoginPage />, {
      path: '/login',
      initialEntry: '/login?returnTo=%2Fbookings',
      extraRoutes: [{ path: '/bookings', element: <p>Bookings page</p> }],
    });
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret@123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('Bookings page')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/bookings');
    expect(useAuthStore.getState().user?.email).toBe('asha@example.com');
    expect(JSON.parse(localStorage.getItem('veloceride.session')!).refreshToken).toBe('refresh-1');
  });

  it('shows a clear message for wrong credentials', async () => {
    server.use(http.post(`${API}/auth/login`, () => fail(401, 'Invalid email or password')));
    renderRoute(<LoginPage />, { path: '/login' });
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText('Incorrect email or password.')).toBeInTheDocument();
    expect(useAuthStore.getState().status).toBe('anonymous');
  });

  it('validates the form before calling the API', async () => {
    let called = false;
    server.use(
      http.post(`${API}/auth/login`, () => {
        called = true;
        return ok(auth);
      }),
    );
    renderRoute(<LoginPage />, { path: '/login' });
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('registration maps a 409 duplicate email onto the email field', async () => {
    server.use(http.post(`${API}/auth/register`, () => fail(409, 'Email is already registered')));
    renderRoute(<RegisterPage />, { path: '/register' });
    await userEvent.type(screen.getByLabelText('Full name'), 'Asha Verma');
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com');
    await userEvent.type(screen.getByLabelText('Phone number'), '+919876543210');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret@123');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Secret@123');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Email is already registered')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });
});

describe('protected routes', () => {
  it('redirects anonymous users to login with a return path', async () => {
    const { router } = renderRoute(
      <ProtectedRoute>
        <p>Secret dashboard</p>
      </ProtectedRoute>,
      { path: '/dashboard', extraRoutes: [{ path: '/login', element: <p>Login page</p> }] },
    );
    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?returnTo=%2Fdashboard');
  });

  it('renders the page for signed-in users', async () => {
    signIn();
    renderRoute(
      <ProtectedRoute>
        <p>Secret dashboard</p>
      </ProtectedRoute>,
      { path: '/dashboard' },
    );
    await waitFor(() => expect(screen.getByText('Secret dashboard')).toBeInTheDocument());
  });
});
