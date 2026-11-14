import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { MailCheck } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Field';
import { Notice } from '@/components/common/Feedback';
import { authService } from '@/services/authService';
import { forgotPasswordSchema } from '@/utils/validation';
import { getErrorMessage } from '@/utils/errors';

export default function ForgotPasswordPage() {
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<{ email: string }>({ resolver: zodResolver(forgotPasswordSchema), defaultValues: { email: '' } });

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null);
    try {
      await authService.forgotPassword(email);
      setSentTo(email);
    } catch (err) {
      setFormError(getErrorMessage(err));
    }
  });

  if (sentTo) {
    return (
      <div className="text-center">
        <MailCheck className="mx-auto size-10 text-accent" aria-hidden />
        <h1 className="mt-4 text-2xl font-semibold tracking-tight">Check your inbox</h1>
        <p className="mt-2 text-muted">
          If <span className="text-ink">{sentTo}</span> is registered, a link to reset your password is on its way. The link is
          valid for 30 minutes.
        </p>
        <Link to="/login" className="mt-8 inline-block font-medium text-accent hover:underline">
          Back to log in
        </Link>
      </div>
    );
  }

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Reset your password</h1>
      <p className="mt-1 text-muted">Enter your account email and we’ll send you a reset link.</p>
      <form onSubmit={onSubmit} noValidate className="mt-8 space-y-4">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <Input label="Email" type="email" autoComplete="email" autoFocus error={errors.email?.message} {...register('email')} />
        <Button type="submit" block size="lg" loading={isSubmitting}>
          Send reset link
        </Button>
      </form>
      <p className="mt-8 text-center text-muted">
        <Link to="/login" className="font-medium text-accent hover:underline">
          Back to log in
        </Link>
      </p>
    </>
  );
}
