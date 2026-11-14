import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Field';
import { Notice } from '@/components/common/Feedback';
import { authService } from '@/services/authService';
import { startSession } from '@/services/session';
import { useAuthFlow, useGoogleSignIn, verificationFromError } from '@/hooks/useAuthFlow';
import { GoogleSignInButton, OrDivider } from '@/components/forms/GoogleSignInButton';
import { safeReturnTo } from '@/constants/routes';
import { loginSchema, type LoginValues } from '@/utils/validation';
import { applyFieldErrors, errorStatus, getErrorMessage } from '@/utils/errors';

export default function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [formError, setFormError] = useState<string | null>(null);
  const handleFlow = useAuthFlow();
  const google = useGoogleSignIn(params.get('returnTo'));
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const auth = await authService.login(values);
      startSession(auth);
      navigate(safeReturnTo(params.get('returnTo')), { replace: true });
    } catch (err) {
      const status = errorStatus(err);
      // Correct password, but email/phone not verified yet: continue to code entry (codes were sent).
      const challenge = verificationFromError(err);
      if (challenge) return handleFlow(challenge, params.get('returnTo'));
      if (status === 401) setFormError('Incorrect email or password.');
      else if (status === 403) setFormError('This account is deactivated. Contact support to reactivate it.');
      else if (!applyFieldErrors(err, setError, ['email', 'password'])) setFormError(getErrorMessage(err));
    }
  });

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Welcome back</h1>
      <p className="mt-1 text-muted">Log in to book or publish rides.</p>

      <div className="mt-8">
        <GoogleSignInButton text="signin_with" onCredential={google.onCredential} disabled={google.busy} />
        {google.error && (
          <Notice tone="danger" className="mt-3">
            {google.error}
          </Notice>
        )}
        <OrDivider />
      </div>

      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <Input label="Email" type="email" autoComplete="email" autoFocus error={errors.email?.message} {...register('email')} />
        <div>
          <Input
            label="Password"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <div className="mt-2 text-right">
            <Link to="/forgot-password" className="text-[13px] text-muted hover:text-accent">
              Forgot password?
            </Link>
          </div>
        </div>
        <Button type="submit" block size="lg" loading={isSubmitting}>
          Log in
        </Button>
      </form>

      <p className="mt-8 text-center text-muted">
        New to VeloceRide?{' '}
        <Link to={`/register${params.get('returnTo') ? `?returnTo=${encodeURIComponent(params.get('returnTo')!)}` : ''}`} className="font-medium text-accent hover:underline">
          Create an account
        </Link>
      </p>
    </>
  );
}
