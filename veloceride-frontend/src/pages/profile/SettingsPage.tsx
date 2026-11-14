import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { LogOut } from 'lucide-react';
import { userService } from '@/services/userService';
import { clearLocalSession, endSession } from '@/services/session';
import { useMode } from '@/hooks/useMode';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/toastStore';
import { PageHeader, Section } from '@/components/layout/PageHeader';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Field';
import { Notice } from '@/components/common/Feedback';
import { Segmented } from '@/components/common/Navigation';
import { ThemeSegmented } from '@/components/common/ThemeControls';
import { ConfirmDialog } from '@/components/modals/ConfirmDialog';
import { changePasswordSchema, PASSWORD_HINT, type ChangePasswordValues } from '@/utils/validation';
import { applyFieldErrors, getErrorMessage } from '@/utils/errors';

function ChangePasswordForm() {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });
  const onSubmit = handleSubmit(async (v) => {
    setFormError(null);
    try {
      await userService.changePassword(v);
      reset();
      toast.success('Password changed', 'Your other devices have been signed out.');
    } catch (err) {
      if (applyFieldErrors(err, setError, ['currentPassword', 'newPassword', 'confirmPassword'])) return;
      const msg = getErrorMessage(err);
      if (/current password/i.test(msg)) return setError('currentPassword', { message: msg });
      if (/different/i.test(msg)) return setError('newPassword', { message: msg });
      setFormError(msg);
    }
  });
  return (
    <form onSubmit={onSubmit} noValidate className="max-w-md space-y-4">
      {formError && <Notice tone="danger">{formError}</Notice>}
      <Input label="Current password" type="password" autoComplete="current-password" error={errors.currentPassword?.message} {...register('currentPassword')} />
      <Input label="New password" type="password" autoComplete="new-password" hint={PASSWORD_HINT} error={errors.newPassword?.message} {...register('newPassword')} />
      <Input label="Confirm new password" type="password" autoComplete="new-password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
      <Button type="submit" loading={isSubmitting}>
        Update password
      </Button>
    </form>
  );
}

/** Page 10: only settings the backend supports (no notification preferences — gap G-13). */
export default function SettingsPage() {
  const navigate = useNavigate();
  const { mode, switchTo, switching } = useMode();
  const user = useAuthStore((s) => s.user);
  const [confirm, setConfirm] = useState<'everywhere' | 'deactivate' | null>(null);

  const signOutEverywhere = useMutation({
    mutationFn: () => endSession(true),
    onSuccess: () => navigate('/', { replace: true }),
  });
  const deactivate = useMutation({
    mutationFn: () => userService.deactivate(),
    onSuccess: () => {
      clearLocalSession();
      toast.info('Account deactivated', 'Contact support if you want it reactivated.');
      navigate('/', { replace: true });
    },
    onError: (err) => {
      toast.error('Couldn’t deactivate account', getErrorMessage(err));
      setConfirm(null);
    },
  });

  return (
    <div className="max-w-3xl">
      <PageHeader title="Settings" />

      <Section title="Appearance" description="Choose a theme. System follows your device’s light or dark setting.">
        <ThemeSegmented />
      </Section>

      <Section title="Default mode" description="Changes what the app shows first. You can always book and publish rides with the same account.">
        <Segmented
          label="Default mode"
          value={mode}
          onChange={switchTo}
          options={[
            { value: 'PASSENGER', label: 'Passenger' },
            { value: 'RIDER', label: 'Rider' },
          ]}
        />
        {switching && <span className="ml-3 text-[13px] text-muted">Saving…</span>}
      </Section>

      <Section title="Password">
        {user?.passwordSet === false ? (
          <p className="max-w-md text-muted">
            You sign in with Google, so there’s no password on this account. To add one, use{' '}
            <Link to="/forgot-password" className="font-medium text-accent hover:underline">
              Forgot password
            </Link>{' '}
            after logging out.
          </p>
        ) : (
          <ChangePasswordForm />
        )}
      </Section>

      <Section title="Sessions" description="Sign out on every device where you’re logged in, including this one.">
        <Button variant="secondary" icon={<LogOut className="size-4" />} onClick={() => setConfirm('everywhere')}>
          Sign out everywhere
        </Button>
      </Section>

      <Section title="Deactivate account" description="Your rides and bookings stay on record. Only VeloceRide support can reactivate the account.">
        <Button variant="danger" onClick={() => setConfirm('deactivate')}>
          Deactivate account
        </Button>
      </Section>

      <ConfirmDialog
        open={confirm === 'everywhere'}
        title="Sign out everywhere?"
        description="You’ll need to log in again on all your devices."
        confirmLabel="Sign out everywhere"
        cancelLabel="Cancel"
        loading={signOutEverywhere.isPending}
        onConfirm={() => signOutEverywhere.mutate()}
        onClose={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm === 'deactivate'}
        title="Deactivate your account?"
        description="You’ll be signed out everywhere and won’t be able to log in until support reactivates the account."
        confirmLabel="Deactivate"
        cancelLabel="Cancel"
        tone="danger"
        loading={deactivate.isPending}
        onConfirm={() => deactivate.mutate()}
        onClose={() => setConfirm(null)}
      />
    </div>
  );
}
