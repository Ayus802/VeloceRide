import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Ticket } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { bookingService } from '@/services/bookingService';
import { useUpcomingBookings } from '@/hooks/queries';
import { PageHeader } from '@/components/layout/PageHeader';
import { Pagination, Tabs } from '@/components/common/Navigation';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { BookingRow } from '@/components/bookings/BookingRow';
import { buttonClass } from '@/components/common/Button';
import type { Booking, BookingStatus } from '@/types/dto';

type Tab = 'upcoming' | 'completed' | 'cancelled' | 'rejected';
const TAB_STATUS: Record<Exclude<Tab, 'upcoming'>, BookingStatus> = {
  completed: 'COMPLETED',
  cancelled: 'CANCELLED',
  rejected: 'REJECTED',
};

function BookingList({ bookings }: { bookings: Booking[] }) {
  return (
    <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
      {bookings.map((b) => (
        <BookingRow key={b.id} booking={b} perspective="passenger" />
      ))}
    </ul>
  );
}

export default function MyBookingsPage() {
  const [params, setParams] = useSearchParams();
  const tab = (['upcoming', 'completed', 'cancelled', 'rejected'].includes(params.get('tab') ?? '') ? params.get('tab') : 'upcoming') as Tab;
  const page = Math.max(0, Number(params.get('page')) || 0);

  const upcoming = useUpcomingBookings();
  const status = tab === 'upcoming' ? undefined : TAB_STATUS[tab];
  const history = useQuery({
    queryKey: qk.myBookings({ status, page }),
    queryFn: () => bookingService.mine({ status, page, size: 20, sort: 'updatedAt,desc' }),
    enabled: tab !== 'upcoming',
    placeholderData: keepPreviousData,
  });

  const empty = (
    <EmptyState
      icon={<Ticket className="size-8" aria-hidden />}
      title={tab === 'upcoming' ? 'No upcoming trips' : `No ${tab} bookings`}
      description={tab === 'upcoming' ? 'Find a ride heading your way and request a seat.' : undefined}
      action={
        tab === 'upcoming' && (
          <Link to="/rides/search" className={buttonClass('primary', 'sm')}>
            Find a ride
          </Link>
        )
      }
    />
  );

  return (
    <>
      <PageHeader title="My trips" description="Rides you’ve booked as a passenger." />
      <Tabs
        className="mb-6"
        value={tab}
        onChange={(t) => setParams({ tab: t })}
        items={[
          { value: 'upcoming', label: 'Upcoming', count: upcoming.bookings.length },
          { value: 'completed', label: 'Completed' },
          { value: 'cancelled', label: 'Cancelled' },
          { value: 'rejected', label: 'Declined' },
        ]}
      />
      {tab === 'upcoming' ? (
        upcoming.isPending ? (
          <ListSkeleton />
        ) : upcoming.error ? (
          <ErrorState error={upcoming.error} onRetry={upcoming.refetch} />
        ) : upcoming.bookings.length === 0 ? (
          empty
        ) : (
          <BookingList bookings={upcoming.bookings} />
        )
      ) : history.isPending ? (
        <ListSkeleton />
      ) : history.isError ? (
        <ErrorState error={history.error} onRetry={() => history.refetch()} />
      ) : history.data!.content.length === 0 ? (
        empty
      ) : (
        <>
          <BookingList bookings={history.data!.content} />
          <Pagination
            page={history.data!.page}
            totalPages={history.data!.totalPages}
            onChange={(p) => setParams({ tab, page: String(p) })}
          />
        </>
      )}
    </>
  );
}
