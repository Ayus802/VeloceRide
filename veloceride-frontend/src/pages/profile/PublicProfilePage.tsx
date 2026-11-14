import { Navigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { userService } from '@/services/userService';
import { useAuthStore } from '@/store/authStore';
import { Avatar } from '@/components/common/Avatar';
import { ErrorState, PageLoader } from '@/components/common/Feedback';
import { ReviewsView } from '@/components/reviews/ReviewsView';
import { formatInstant } from '@/utils/format';

/** Another user's public profile — the backend never exposes contact details here. */
export default function PublicProfilePage() {
  const { userId = '' } = useParams();
  const me = useAuthStore((s) => s.user);
  const query = useQuery({ queryKey: qk.publicUser(userId), queryFn: () => userService.publicProfile(userId) });

  if (me?.id === userId) return <Navigate to="/profile" replace />;
  if (query.isPending) return <PageLoader />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const u = query.data;

  return (
    <div className="max-w-3xl">
      <div className="mb-10 flex items-center gap-5">
        <Avatar name={u.fullName} src={u.profilePicture} size="lg" />
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{u.fullName}</h1>
          <p className="text-muted">Member since {formatInstant(u.memberSince, true)}</p>
        </div>
      </div>
      <ReviewsView userId={u.id} />
    </div>
  );
}
