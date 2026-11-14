import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Field';
import { Notice } from '@/components/common/Feedback';
import { authService } from '@/services/authService';
import { useAuthFlow, useGoogleSignIn } from '@/hooks/useAuthFlow';
import { GoogleSignInButton, OrDivider } from '@/components/forms/GoogleSignInButton';
import { PASSWORD_HINT, registerSchema, type RegisterValues } from '@/utils/validation';
import { applyFieldErrors, errorStatus, getErrorMessage } from '@/utils/errors';

const FIELDS = ['fullName', 'email', 'phoneNumber', 'password', 'confirmPassword'] as const;

/** One account for both booking and publishing rides — no passenger/driver choice here. */
export default function RegisterPage() {
  const [params] = useSearchParams();
  const handleFlow = useAuthFlow();
  const google = useGoogleSignIn(params.get('returnTo'));
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', phoneNumber: '', password: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      // Account is created unverified; codes go to the email and the phone number.
      handleFlow(await authService.register(values), params.get('returnTo'));
    } catch (err) {
      if (applyFieldErrors(err, setError, FIELDS)) return;
      const message = getErrorMessage(err);
      if (errorStatus(err) === 409) {
        // "Email is already registered" / "Phone number is already registered"
        if (/email/i.test(message)) return setError('email', { message });
        if (/phone/i.test(message)) return setError('phoneNumber', { message });
      }
      setFormError(message);
    }
  });

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Create your account</h1>
      <p className="mt-1 text-muted">One account to find rides and to offer them.</p>

      <div className="mt-8">
        <GoogleSignInButton text="signup_with" onCredential={google.onCredential} disabled={google.busy} />
        {google.error && (
          <Notice tone="danger" className="mt-3">
            {google.error}
          </Notice>
        )}
        <OrDivider />
      </div>

      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <Input label="Full name" autoComplete="name" autoFocus error={errors.fullName?.message} {...register('fullName')} />
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Input
          label="Phone number"
          type="tel"
          autoComplete="tel"
          placeholder="+919876543210"
          hint="We’ll text you a code to verify it. Shared with co-travellers only after a booking is confirmed."
          error={errors.phoneNumber?.message}
          {...register('phoneNumber')}
        />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          hint={PASSWORD_HINT}
          error={errors.password?.message}
          {...register('password')}
        />
        <Input
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <Button type="submit" block size="lg" loading={isSubmitting}>
          Create account
        </Button>
        <p className="text-center text-[13px] text-faint">We’ll send a code to your email and phone to confirm them.</p>
      </form>

      <p className="mt-8 text-center text-muted">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-accent hover:underline">
          Log in
        </Link>
      </p>
    </>
  );
}
