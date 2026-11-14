import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { BadgeCheck, Pencil } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { userService } from '@/services/userService';
import { useAuthStore } from '@/store/authStore';
import { PageHeader, Panel, DetailList, Section } from '@/components/layout/PageHeader';
import { Avatar } from '@/components/common/Avatar';
import { RatingInline } from '@/components/common/Rating';
import { ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { buttonClass } from '@/components/common/Button';
import { ReviewList } from '@/components/reviews/ReviewList';
import { Badge } from '@/components/common/StatusBadge';
import { VerifyContactModal } from '@/components/modals/VerifyContactModal';
import type { VerificationChannel } from '@/types/dto';
import { formatInstant } from '@/utils/format';

export default function ProfilePage() {
  const storeUser = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const me = useQuery({
    queryKey: qk.me,
    queryFn: async () => {
      const u = await userService.me();
      setUser(u);
      return u;
    },
    initialData: storeUser ?? undefined,
    staleTime: 0,
  });
  const user = me.data;
  const [params, setParams] = useSearchParams();
  const verifyParam = params.get('verify');
  const [verifying, setVerifying] = useState<VerificationChannel | null>(
    verifyParam === 'PHONE' || verifyParam === 'EMAIL' ? verifyParam : null,
  );
  const reviews = useQuery({
    queryKey: qk.userReviews(user?.id ?? '', { size: 5 }),
    queryFn: () => userService.reviews(user!.id, { size: 5 }),
    enabled: Boolean(user),
  });

  if (!user) return me.isError ? <ErrorState error={me.error} onRetry={() => me.refetch()} /> : null;

  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Profile"
        actions={
          <Link to="/profile/edit" className={buttonClass('secondary', 'sm')}>
            <Pencil className="size-3.5" aria-hidden /> Edit profile
          </Link>
        }
      />
      <div className="mb-8 flex items-center gap-5">
        <Avatar name={user.fullName} src={user.profilePicture} size="lg" />
        <div>
          <p className="text-lg font-semibold">{user.fullName}</p>
          <RatingInline value={user.averageRating} count={user.totalReviews} />
        </div>
      </div>

      <Panel>
        <DetailList
          items={[
            {
              label: 'Email',
              value: (
                <ContactValue value={user.email} verified={user.emailVerified} onVerify={() => setVerifying('EMAIL')} />
              ),
            },
            {
              label: 'Phone',
              value: (
                <ContactValue value={user.phoneNumber} verified={user.phoneVerified} onVerify={() => setVerifying('PHONE')} />
              ),
            },
            user.googleLinked ? { label: 'Sign-in', value: user.passwordSet ? 'Password and Google' : 'Google' } : null,
            { label: 'Mode', value: user.activeMode === 'RIDER' ? 'Rider' : 'Passenger' },
            { label: 'Member since', value: formatInstant(user.createdAt, true) },
          ]}
        />
      </Panel>

      {verifying && (
        <VerifyContactModal
          channel={verifying}
          open
          onClose={() => {
            setVerifying(null);
            if (verifyParam) setParams({}, { replace: true });
          }}
        />
      )}

      <Section
        title="Recent reviews"
        action={
          (reviews.data?.reviews.totalElements ?? 0) > 5 && (
            <Link to="/reviews" className="text-[13px] text-accent hover:underline">
              See all
            </Link>
          )
        }
      >
        {reviews.isPending ? (
          <ListSkeleton rows={2} />
        ) : reviews.isError ? (
          <ErrorState error={reviews.error} onRetry={() => reviews.refetch()} />
        ) : (
          <ReviewList reviews={reviews.data.reviews.content} />
        )}
      </Section>
    </div>
  );
}

function ContactValue({ value, verified, onVerify }: { value: string; verified: boolean; onVerify: () => void }) {
  return (
    <span className="inline-flex flex-wrap items-center justify-end gap-2">
      <span>{value}</span>
      {verified ? (
        <BadgeCheck className="size-4 text-success" aria-label="Verified" />
      ) : (
        <>
          <Badge tone="warning">Not verified</Badge>
          <button type="button" onClick={onVerify} className="text-[13px] font-medium text-accent hover:underline">
            Verify
          </button>
        </>
      )}
    </span>
  );
}
