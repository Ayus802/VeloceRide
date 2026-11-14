import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { adminService } from '@/services/adminService';
import { PageHeader } from '@/components/layout/PageHeader';
import { DataTable, FilterSelect } from '@/components/common/DataTable';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination } from '@/components/common/Navigation';
import { BookingStatusBadge } from '@/components/common/StatusBadge';
import { BOOKING_STATUS_LABEL } from '@/constants/labels';
import type { Booking, BookingStatus } from '@/types/dto';
import { formatDate, formatMoney } from '@/utils/format';

/** Page 32 — read-only list with status filter (gap G-14: no admin booking actions). */
export default function AdminBookingsPage() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as BookingStatus | '';
  const page = Math.max(0, Number(params.get('page')) || 0);
  const query = useQuery({
    queryKey: [...qk.admin, 'bookings', { status, page }],
    queryFn: () => adminService.bookings({ status: status || undefined, page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader
        title="Bookings"
        actions={
          <FilterSelect<BookingStatus>
            label="Status"
            value={status}
            onChange={(v) => setParams(v ? { status: v } : {})}
            options={[{ value: '', label: 'All' }, ...(Object.keys(BOOKING_STATUS_LABEL) as BookingStatus[]).map((s) => ({ value: s, label: BOOKING_STATUS_LABEL[s] }))]}
          />
        }
      />
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState title="No bookings" />
      ) : (
        <>
          <DataTable<Booking>
            caption="Bookings"
            rows={query.data.content}
            rowKey={(b) => b.id}
            columns={[
              {
                header: 'Reference',
                cell: (b) => (
                  <Link to={`/bookings/${b.id}`} className="font-mono text-[13px] hover:text-accent">
                    {b.bookingReference}
                  </Link>
                ),
              },
              { header: 'Route', cell: (b) => `${b.ride.source} → ${b.ride.destination}` },
              { header: 'Date', cell: (b) => <span className="text-muted">{formatDate(b.ride.departureDate)}</span>, hideOnMobile: true },
              { header: 'Passenger', cell: (b) => b.passenger.fullName, hideOnMobile: true },
              { header: 'Driver', cell: (b) => b.driver.fullName, hideOnMobile: true },
              { header: 'Amount', cell: (b) => formatMoney(b.totalAmount), hideOnMobile: true },
              { header: 'Status', cell: (b) => <BookingStatusBadge status={b.bookingStatus} /> },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ ...(status ? { status } : {}), page: String(p) })} />
        </>
      )}
    </>
  );
}
