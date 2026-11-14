import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { adminService } from '@/services/adminService';
import { PageHeader } from '@/components/layout/PageHeader';
import { DataTable, FilterSelect } from '@/components/common/DataTable';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination } from '@/components/common/Navigation';
import { RideStatusBadge } from '@/components/common/StatusBadge';
import { RIDE_STATUS_LABEL } from '@/constants/labels';
import type { Ride, RideStatus } from '@/types/dto';
import { formatDate, formatMoney, formatTime } from '@/utils/format';

/** Page 31 — read-only: the backend offers no admin ride actions (gap G-14). */
export default function AdminRidesPage() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as RideStatus | '';
  const page = Math.max(0, Number(params.get('page')) || 0);
  const query = useQuery({
    queryKey: [...qk.admin, 'rides', { status, page }],
    queryFn: () => adminService.rides({ status: status || undefined, page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader
        title="Rides"
        actions={
          <FilterSelect<RideStatus>
            label="Status"
            value={status}
            onChange={(v) => setParams(v ? { status: v } : {})}
            options={[{ value: '', label: 'All' }, ...(Object.keys(RIDE_STATUS_LABEL) as RideStatus[]).map((s) => ({ value: s, label: RIDE_STATUS_LABEL[s] }))]}
          />
        }
      />
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState title="No rides" />
      ) : (
        <>
          <DataTable<Ride>
            caption="Rides"
            rows={query.data.content}
            rowKey={(r) => r.id}
            columns={[
              {
                header: 'Route',
                cell: (r) => (
                  <Link to={`/rides/${r.id}`} className="font-medium hover:text-accent">
                    {r.source} → {r.destination}
                  </Link>
                ),
              },
              { header: 'Departure', cell: (r) => <span className="text-muted">{formatDate(r.departureDate)} · {formatTime(r.departureTime)}</span> },
              { header: 'Driver', cell: (r) => r.driver.fullName, hideOnMobile: true },
              { header: 'Seats', cell: (r) => <span className="tabular-nums">{r.bookedSeats}/{r.totalSeats}</span>, hideOnMobile: true },
              { header: 'Price', cell: (r) => formatMoney(r.pricePerSeat), hideOnMobile: true },
              { header: 'Status', cell: (r) => <RideStatusBadge status={r.rideStatus} /> },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ ...(status ? { status } : {}), page: String(p) })} />
        </>
      )}
    </>
  );
}
