import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Inbox } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { bookingService } from '@/services/bookingService';
import { PageHeader } from '@/components/layout/PageHeader';
import { Pagination } from '@/components/common/Navigation';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { BookingRow } from '@/components/bookings/BookingRow';
import { RequestActions } from '@/components/bookings/RequestActions';

/** Page 22: pending requests across all my rides. */
export default function BookingRequestsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const query = useQuery({
    queryKey: qk.received({ status: 'PENDING', page }),
    queryFn: () => bookingService.received({ status: 'PENDING', page, size: 20, sort: 'createdAt,asc' }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="Booking requests" description="Passengers waiting for your answer, oldest first." />
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState icon={<Inbox className="size-8" aria-hidden />} title="No pending requests" description="New requests on your rides will show up here." />
      ) : (
        <>
          <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
            {query.data.content.map((b) => (
              <BookingRow key={b.id} booking={b} perspective="driver" actions={<RequestActions booking={b} />} />
            ))}
          </ul>
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ page: String(p) })} />
        </>
      )}
    </>
  );
}
