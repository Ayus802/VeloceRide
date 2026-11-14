import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Car, Plus } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { rideService } from '@/services/rideService';
import { PageHeader } from '@/components/layout/PageHeader';
import { Pagination, Tabs } from '@/components/common/Navigation';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { RideRow } from '@/components/rides/RideBits';
import { buttonClass } from '@/components/common/Button';
import type { RideStatus } from '@/types/dto';

type Tab = 'upcoming' | 'ongoing' | 'completed' | 'cancelled';
const TAB: Record<Tab, { status: RideStatus; sort: string[]; label: string }> = {
  upcoming: { status: 'SCHEDULED', sort: ['departureDate,asc', 'departureTime,asc'], label: 'Upcoming' },
  ongoing: { status: 'ONGOING', sort: ['departureDate,desc'], label: 'On the road' },
  completed: { status: 'COMPLETED', sort: ['departureDate,desc', 'departureTime,desc'], label: 'Completed' },
  cancelled: { status: 'CANCELLED', sort: ['departureDate,desc'], label: 'Cancelled' },
};

export default function MyRidesPage() {
  const [params, setParams] = useSearchParams();
  const tab = ((params.get('tab') ?? 'upcoming') in TAB ? params.get('tab') ?? 'upcoming' : 'upcoming') as Tab;
  const page = Math.max(0, Number(params.get('page')) || 0);
  const { status, sort } = TAB[tab];

  const query = useQuery({
    queryKey: qk.myRides({ status, page }),
    queryFn: () => rideService.mine({ status, page, size: 20, sort }),
    placeholderData: keepPreviousData,
  });
  const ongoing = useQuery({
    queryKey: qk.myRides({ status: 'ONGOING', count: true }),
    queryFn: () => rideService.mine({ status: 'ONGOING', size: 1 }),
  });

  return (
    <>
      <PageHeader
        title="My rides"
        description="Rides you’re driving."
        actions={
          <Link to="/rides/new" className={buttonClass('primary', 'sm')}>
            <Plus className="size-4" aria-hidden /> Publish
          </Link>
        }
      />
      <Tabs
        className="mb-6"
        value={tab}
        onChange={(t) => setParams({ tab: t })}
        items={(Object.keys(TAB) as Tab[]).map((t) => ({
          value: t,
          label: TAB[t].label,
          count: t === 'ongoing' ? ongoing.data?.totalElements : undefined,
        }))}
      />
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState
          icon={<Car className="size-8" aria-hidden />}
          title={tab === 'upcoming' ? 'No upcoming rides' : `No ${TAB[tab].label.toLowerCase()} rides`}
          description={tab === 'upcoming' ? 'Publish a ride and passengers heading the same way can book your empty seats.' : undefined}
          action={
            tab === 'upcoming' && (
              <Link to="/rides/new" className={buttonClass('primary', 'sm')}>
                Publish a ride
              </Link>
            )
          }
        />
      ) : (
        <>
          <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
            {query.data.content.map((ride) => (
              <RideRow key={ride.id} ride={ride} to={`/my-rides/${ride.id}`} showDate />
            ))}
          </ul>
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ tab, page: String(p) })} />
        </>
      )}
    </>
  );
}
