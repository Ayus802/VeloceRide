import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { userService } from '@/services/userService';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, buttonClass } from '@/components/common/Button';
import { Input } from '@/components/common/Field';
import { Notice } from '@/components/common/Feedback';
import { Avatar } from '@/components/common/Avatar';
import { profileSchema, type ProfileValues } from '@/utils/validation';
import { applyFieldErrors, errorStatus, getErrorMessage } from '@/utils/errors';

/** Page 07: the backend allows name, phone and picture URL; email is the login identity. */
export default function EditProfilePage() {
  const user = useAuthStore((s) => s.user)!;
  const setUser = useAuthStore((s) => s.setUser);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user.fullName, phoneNumber: user.phoneNumber, profilePicture: user.profilePicture ?? '' },
  });
  const picture = watch('profilePicture');
  const name = watch('fullName');

  const onSubmit = handleSubmit(async (v) => {
    setFormError(null);
    try {
      const updated = await userService.updateMe({
        fullName: v.fullName.trim(),
        phoneNumber: v.phoneNumber.trim(),
        profilePicture: v.profilePicture.trim() || undefined,
      });
      setUser(updated);
      queryClient.setQueryData(qk.me, updated);
      if (!updated.phoneVerified) {
        toast.info('Verify your new phone number', 'We’re sending a code to it now.');
        navigate('/profile?verify=PHONE');
      } else {
        toast.success('Profile updated');
        navigate('/profile');
      }
    } catch (err) {
      if (applyFieldErrors(err, setError, ['fullName', 'phoneNumber', 'profilePicture'])) return;
      if (errorStatus(err) === 409) return setError('phoneNumber', { message: getErrorMessage(err) });
      setFormError(getErrorMessage(err));
    }
  });

  return (
    <div className="max-w-xl">
      <PageHeader title="Edit profile" back={{ to: '/profile', label: 'Profile' }} />
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <div className="flex items-center gap-4">
          <Avatar name={name} src={/^https?:\/\//.test(picture) ? picture : undefined} size="lg" />
          <Input
            containerClassName="flex-1"
            type="url"
            label="Profile picture URL"
            optional
            placeholder="https://…"
            error={errors.profilePicture?.message}
            {...register('profilePicture')}
          />
        </div>
        <Input label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        <Input
          label="Phone number"
          type="tel"
          autoComplete="tel"
          hint="Changing it requires confirming the new number with a code."
          error={errors.phoneNumber?.message}
          {...register('phoneNumber')}
        />
        <Input label="Email" value={user.email} readOnly hint="Email is your login and can’t be changed." />
        <div className="flex flex-col-reverse gap-2 border-t border-line pt-6 sm:flex-row sm:justify-end">
          <Link to="/profile" className={buttonClass('ghost')}>
            Cancel
          </Link>
          <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
