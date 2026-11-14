import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginPage from '@/pages/auth/LoginPage';
import RegisterPage from '@/pages/auth/RegisterPage';
import VerifyPage from '@/pages/auth/VerifyPage';
import CompleteSignupPage from '@/pages/auth/CompleteSignupPage';
import { GoogleSignInButton } from '@/components/forms/GoogleSignInButton';
import { pendingFlow, useGoogleSignIn } from '@/hooks/useAuthFlow';
import { useAuthStore } from '@/store/authStore';
import { API, auth, fail, http, ok, server } from '@/test/server';
import { renderRoute } from '@/test/render';
import type { AuthFlowResponse, VerificationChallenge } from '@/types/dto';

const challenge = (emailVerified = false, phoneVerified = false): VerificationChallenge => ({
  verificationToken: 'vt-1',
  expiresIn: 1800,
  channels: [
    { channel: 'EMAIL', destination: 'a***@example.com', verified: emailVerified, codeSent: !emailVerified, resendAvailableInSeconds: 0 },
    { channel: 'PHONE', destination: '+91******3210', verified: phoneVerified, codeSent: !phoneVerified, resendAvailableInSeconds: 45 },
  ],
});
const pending = (c = challenge()): AuthFlowResponse => ({ status: 'VERIFICATION_REQUIRED', verification: c });

const verifyRoute = [{ path: '/verify', element: <VerifyPage /> }];

describe('registration with email + phone OTP', () => {
  it('sends the user to code entry instead of signing in', async () => {
    server.use(http.post(`${API}/auth/register`, () => ok(pending(), 201)));
    renderRoute(<RegisterPage />, { path: '/register', extraRoutes: verifyRoute });
    await userEvent.type(screen.getByLabelText('Full name'), 'Asha Verma');
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com');
    await userEvent.type(screen.getByLabelText('Phone number'), '+919876543210');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret@123');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Secret@123');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: 'Verify your account' })).toBeInTheDocument();
    expect(screen.getByText('a***@example.com')).toBeInTheDocument();
    expect(useAuthStore.getState().status).toBe('anonymous'); // no tokens yet
  });

  it('login of an unverified account continues to code entry', async () => {
    server.use(http.post(`${API}/auth/login`, () =>
      Response.json({ success: false, message: 'Verify your email address and phone number to continue', data: pending(), errors: [] }, { status: 403 }),
    ));
    renderRoute(<LoginPage />, { path: '/login', extraRoutes: verifyRoute });
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret@123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByRole('heading', { name: 'Verify your account' })).toBeInTheDocument();
  });

  it('a deactivated account still gets the deactivated message', async () => {
    server.use(http.post(`${API}/auth/login`, () => fail(403, 'Account is deactivated')));
    renderRoute(<LoginPage />, { path: '/login' });
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret@123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText(/deactivated/)).toBeInTheDocument();
  });
});

describe('verify page', () => {
  it('confirms both codes, then signs in', async () => {
    pendingFlow.write({ verification: challenge(), returnTo: '/bookings' });
    const bodies: unknown[] = [];
    server.use(
      http.post(`${API}/auth/verification/confirm`, async ({ request }) => {
        const body = (await request.json()) as { channel: string };
        bodies.push(body);
        return body.channel === 'EMAIL' ? ok(pending(challenge(true))) : ok({ status: 'AUTHENTICATED', auth });
      }),
    );
    const { router } = renderRoute(<VerifyPage />, {
      path: '/verify',
      extraRoutes: [{ path: '/bookings', element: <p>Bookings page</p> }],
    });

    await userEvent.type(screen.getByLabelText('Email address code'), '123456'); // auto-submits at 6 digits
    expect(await screen.findByText('a***@example.com · verified')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Phone number code'), '654321');
    expect(await screen.findByText('Bookings page')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/bookings');
    expect(bodies).toEqual([
      { verificationToken: 'vt-1', channel: 'EMAIL', code: '123456' },
      { verificationToken: 'vt-1', channel: 'PHONE', code: '654321' },
    ]);
    expect(useAuthStore.getState().status).toBe('authenticated');
    expect(pendingFlow.read()).toBeNull();
  });

  it('shows wrong-code errors and honours the resend cooldown', async () => {
    pendingFlow.write({ verification: challenge() });
    server.use(http.post(`${API}/auth/verification/confirm`, () => fail(400, 'Incorrect code. 4 attempts left.')));
    renderRoute(<VerifyPage />, { path: '/verify' });
    await userEvent.type(screen.getByLabelText('Email address code'), '000000');
    expect(await screen.findByText('Incorrect code. 4 attempts left.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Resend code in \d+s/ })).toBeDisabled(); // phone: 45 s cooldown
  });

  it('explains an expired session', async () => {
    pendingFlow.write({ verification: challenge() });
    server.use(http.post(`${API}/auth/verification/confirm`, () => fail(401, 'This verification session has expired.')));
    renderRoute(<VerifyPage />, { path: '/verify' });
    await userEvent.type(screen.getByLabelText('Email address code'), '111111');
    expect(await screen.findByText('This verification session has expired')).toBeInTheDocument();
  });

  it('without a pending flow, points to log in', () => {
    renderRoute(<VerifyPage />, { path: '/verify' });
    expect(screen.getByText('Nothing to verify')).toBeInTheDocument();
  });
});

describe('Google sign-in', () => {
  function GoogleHarness({ token }: { token: string }) {
    const { onCredential, error } = useGoogleSignIn('/dashboard');
    return (
      <>
        <button type="button" onClick={() => void onCredential(token)}>
          Simulate Google
        </button>
        {error && <p>{error}</p>}
      </>
    );
  }

  it('hides the button when no client id is configured', () => {
    const { container } = renderRoute(<GoogleSignInButton onCredential={() => {}} />);
    expect(container.querySelector('[aria-busy]')).toBeNull();
    expect(screen.queryByText(/Google/)).not.toBeInTheDocument();
  });

  it('new Google users add a phone number, then verify it', async () => {
    let completeBody: unknown;
    server.use(
      http.post(`${API}/auth/google`, () =>
        ok({ status: 'PHONE_REQUIRED', googleSignup: { signupToken: 'st-1', expiresIn: 900, email: 'gita@gmail.com', fullName: 'Gita Rao' } }),
      ),
      http.post(`${API}/auth/google/complete`, async ({ request }) => {
        completeBody = await request.json();
        return ok(pending(challenge(true)), 201);
      }),
    );
    renderRoute(<GoogleHarness token="google-id-token" />, {
      path: '/register',
      extraRoutes: [{ path: '/complete-signup', element: <CompleteSignupPage /> }, ...verifyRoute],
    });
    await userEvent.click(screen.getByRole('button', { name: 'Simulate Google' }));
    expect(await screen.findByText('gita@gmail.com · verified by Google')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Phone number'), '+919812345678');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByRole('heading', { name: 'Verify your account' })).toBeInTheDocument();
    expect(completeBody).toEqual({ signupToken: 'st-1', phoneNumber: '+919812345678' });
    expect(screen.getByText('a***@example.com · verified')).toBeInTheDocument(); // email verified by Google
  });

  it('returning Google users are signed in directly', async () => {
    server.use(http.post(`${API}/auth/google`, () => ok({ status: 'AUTHENTICATED', auth })));
    renderRoute(<GoogleHarness token="google-id-token" />, {
      path: '/login',
      extraRoutes: [{ path: '/dashboard', element: <p>Dashboard</p> }],
    });
    await userEvent.click(screen.getByRole('button', { name: 'Simulate Google' }));
    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
    expect(useAuthStore.getState().user?.email).toBe('asha@example.com');
  });

  it('shows the backend error for a rejected Google token', async () => {
    server.use(http.post(`${API}/auth/google`, () => fail(401, 'Google sign-in failed. Please try again.')));
    renderRoute(<GoogleHarness token="bad" />, { path: '/login' });
    await userEvent.click(screen.getByRole('button', { name: 'Simulate Google' }));
    expect(await screen.findByText('Google sign-in failed. Please try again.')).toBeInTheDocument();
  });
});
