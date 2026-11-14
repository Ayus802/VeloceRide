import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, buttonClass } from '@/components/common/Button';
import { Input } from '@/components/common/Field';
import { EmptyState, Notice } from '@/components/common/Feedback';
import { authService } from '@/services/authService';
import { clearLocalSession } from '@/services/session';
import { toast } from '@/store/toastStore';
import { PASSWORD_HINT, resetPasswordSchema, type ResetPasswordValues } from '@/utils/validation';
import { applyFieldErrors, getErrorMessage } from '@/utils/errors';

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  });

  if (!token) {
    return (
      <EmptyState
        title="This reset link is incomplete"
        description="Open the link from your email again, or request a new one."
        action={
          <Link to="/forgot-password" className={buttonClass('primary')}>
            Request a new link
          </Link>
        }
      />
    );
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await authService.resetPassword({ token, ...values });
      clearLocalSession(); // the backend signs out every session after a reset
      toast.success('Password updated', 'Log in with your new password.');
      navigate('/login', { replace: true });
    } catch (err) {
      if (!applyFieldErrors(err, setError, ['newPassword', 'confirmPassword'])) setFormError(getErrorMessage(err));
    }
  });

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Choose a new password</h1>
      <p className="mt-1 text-muted">You’ll be signed out of all devices afterwards.</p>
      <form onSubmit={onSubmit} noValidate className="mt-8 space-y-4">
        {formError && (
          <Notice tone="danger">
            {formError}{' '}
            <Link to="/forgot-password" className="underline">
              Request a new link
            </Link>
          </Notice>
        )}
        <Input
          label="New password"
          type="password"
          autoComplete="new-password"
          autoFocus
          hint={PASSWORD_HINT}
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <Input
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <Button type="submit" block size="lg" loading={isSubmitting}>
          Update password
        </Button>
      </form>
    </>
  );
}
