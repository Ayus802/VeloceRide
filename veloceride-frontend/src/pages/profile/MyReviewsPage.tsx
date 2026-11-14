import { useAuthStore } from '@/store/authStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { ReviewsView } from '@/components/reviews/ReviewsView';

export default function MyReviewsPage() {
  const userId = useAuthStore((s) => s.user!.id);
  return (
    <div className="max-w-3xl">
      <PageHeader title="Reviews & ratings" description="What co-travellers said about you." />
      <ReviewsView userId={userId} />
    </div>
  );
}
