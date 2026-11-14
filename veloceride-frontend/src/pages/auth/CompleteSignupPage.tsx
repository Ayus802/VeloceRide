import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { authService } from '@/services/authService';
import { pendingFlow, useAuthFlow } from '@/hooks/useAuthFlow';
import { Avatar } from '@/components/common/Avatar';
import { Button, buttonClass } from '@/components/common/Button';
import { Input } from '@/components/common/Field';
import { EmptyState, Notice } from '@/components/common/Feedback';
import { PHONE_REGEX } from '@/utils/validation';
import { applyFieldErrors, errorStatus, getErrorMessage } from '@/utils/errors';

const schema = z.object({
  phoneNumber: z.string().trim().regex(PHONE_REGEX, 'Enter 8–15 digits, optionally starting with +'),
});

/** New Google users add (and then verify) a phone number — the platform requires one. */
export default function CompleteSignupPage() {
  const [flow] = useState(() => pendingFlow.read());
  const signup = flow?.googleSignup;
  const handleFlow = useAuthFlow();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<{ phoneNumber: string }>({ resolver: zodResolver(schema), defaultValues: { phoneNumber: '' } });

  if (!signup) {
    return (
      <EmptyState
        title="Your Google sign-up session has ended"
        description="Choose “Continue with Google” again to restart."
        action={
          <Link to="/register" className={buttonClass('primary')}>
            Back to sign up
          </Link>
        }
      />
    );
  }

  const onSubmit = handleSubmit(async ({ phoneNumber }) => {
    setFormError(null);
    try {
      handleFlow(await authService.completeGoogleSignup(signup.signupToken, phoneNumber.trim()), flow?.returnTo);
    } catch (err) {
      if (applyFieldErrors(err, setError, ['phoneNumber'])) return;
      const message = getErrorMessage(err);
      if (errorStatus(err) === 409 && /phone/i.test(message)) return setError('phoneNumber', { message });
      setFormError(message);
    }
  });

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Almost there</h1>
      <p className="mt-1 text-muted">Add your phone number so drivers and passengers can reach you once a booking is confirmed.</p>

      <div className="mt-8 flex items-center gap-3 rounded-xl border border-line bg-bg-2 p-4 shadow-card">
        <Avatar name={signup.fullName} src={signup.profilePicture} />
        <div className="min-w-0">
          <p className="truncate font-medium">{signup.fullName}</p>
          <p className="truncate text-[13px] text-muted">{signup.email} · verified by Google</p>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <Input
          label="Phone number"
          type="tel"
          autoComplete="tel"
          autoFocus
          placeholder="+919876543210"
          hint="We’ll text you a code to confirm it."
          error={errors.phoneNumber?.message}
          {...register('phoneNumber')}
        />
        <Button type="submit" block size="lg" loading={isSubmitting}>
          Continue
        </Button>
      </form>
    </>
  );
}
