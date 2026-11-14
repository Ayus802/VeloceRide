import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowDownUp, Check } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { userService } from '@/services/userService';
import { ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination } from '@/components/common/Navigation';
import { Stars } from '@/components/common/Rating';
import { Dropdown, DropdownItem } from '@/components/dropdowns/Dropdown';
import { ReviewList } from '@/components/reviews/ReviewList';
import { formatRating, plural } from '@/utils/format';

const SORTS = [
  { value: 'createdAt,desc', label: 'Newest' },
  { value: 'rating,desc', label: 'Highest rated' },
  { value: 'rating,asc', label: 'Lowest rated' },
];

/** Rating summary + sortable, paginated reviews of a user (own reviews page and public profiles). */
export function ReviewsView({ userId }: { userId: string }) {
  const [params, setParams] = useSearchParams();
  const sort = SORTS.find((s) => s.value === params.get('sort'))?.value ?? SORTS[0].value;
  const page = Math.max(0, Number(params.get('page')) || 0);
  const query = useQuery({
    queryKey: qk.userReviews(userId, { sort, page }),
    queryFn: () => userService.reviews(userId, { sort, page, size: 10 }),
    placeholderData: keepPreviousData,
  });

  if (query.isPending) return <ListSkeleton />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const { rating, reviews } = query.data;

  return (
    <>
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4 border-b border-line pb-6">
        <div className="flex items-center gap-4">
          <span className="text-4xl font-semibold tabular-nums">{formatRating(rating.averageRating)}</span>
          <div>
            {rating.averageRating != null && <Stars value={Math.round(rating.averageRating)} size="md" />}
            <p className="text-[13px] text-muted">{plural(rating.totalReviews, 'review')}</p>
          </div>
        </div>
        {reviews.totalElements > 1 && (
          <Dropdown
            trigger={(props) => (
              <button type="button" className="inline-flex h-9 items-center gap-2 rounded-lg border border-line px-3 text-[13px] text-muted hover:text-ink" {...props}>
                <ArrowDownUp className="size-3.5" aria-hidden />
                {SORTS.find((s) => s.value === sort)!.label}
              </button>
            )}
          >
            {SORTS.map((s) => (
              <DropdownItem key={s.value} icon={s.value === sort ? <Check /> : <span className="size-4" />} onSelect={() => setParams({ sort: s.value })}>
                {s.label}
              </DropdownItem>
            ))}
          </Dropdown>
        )}
      </div>
      <ReviewList reviews={reviews.content} />
      <Pagination page={reviews.page} totalPages={reviews.totalPages} onChange={(p) => setParams({ sort, page: String(p) })} />
    </>
  );
}
