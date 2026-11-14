import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { adminService } from '@/services/adminService';
import { PageHeader, Section } from '@/components/layout/PageHeader';
import { ErrorState, PageLoader } from '@/components/common/Feedback';
import { BOOKING_STATUS_LABEL, RIDE_STATUS_LABEL } from '@/constants/labels';
import type { BookingStatus, RideStatus } from '@/types/dto';
import { formatMoney } from '@/utils/format';

function Stat({ label, value, to }: { label: string; value: string | number; to?: string }) {
  const body = (
    <>
      <dt className="text-[13px] text-muted">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold tabular-nums">{value}</dd>
    </>
  );
  return to ? (
    <Link to={to} className="block rounded-xl border border-line p-4 transition-colors hover:border-line-strong">
      {body}
    </Link>
  ) : (
    <div className="rounded-xl border border-line p-4">{body}</div>
  );
}

/** Page 29: minimal operational overview from GET /admin/stats. */
export default function AdminDashboardPage() {
  const query = useQuery({ queryKey: [...qk.admin, 'stats'], queryFn: adminService.stats });
  if (query.isPending) return <PageLoader />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const s = query.data;

  return (
    <>
      <PageHeader title="Overview" />
      {s.pendingVehicleVerifications > 0 && (
        <Link
          to="/admin/vehicles"
          className="mb-8 flex items-center justify-between gap-3 rounded-xl border border-warning/30 bg-warning/5 px-4 py-3 text-warning"
        >
          <span>
            {s.pendingVehicleVerifications} vehicle{s.pendingVehicleVerifications > 1 ? 's' : ''} waiting for verification
          </span>
          <ArrowRight className="size-4" aria-hidden />
        </Link>
      )}
      <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Active users" value={s.activeUsers} to="/admin/users" />
        <Stat label="Verified vehicles" value={s.verifiedVehicles} to="/admin/vehicles" />
        <Stat label="Rides" value={s.totalRides} to="/admin/rides" />
        <Stat label="Successful payments" value={formatMoney(s.totalSuccessfulPayments)} />
      </dl>

      <div className="grid gap-10 md:grid-cols-2">
        <Section title="Rides by status">
          <dl className="divide-y divide-line">
            {(Object.keys(RIDE_STATUS_LABEL) as RideStatus[]).map((k) => (
              <div key={k} className="flex justify-between py-2.5">
                <dt className="text-muted">{RIDE_STATUS_LABEL[k]}</dt>
                <dd className="tabular-nums">{s.ridesByStatus[k] ?? 0}</dd>
              </div>
            ))}
          </dl>
        </Section>
        <Section title="Bookings by status">
          <dl className="divide-y divide-line">
            {(Object.keys(BOOKING_STATUS_LABEL) as BookingStatus[]).map((k) => (
              <div key={k} className="flex justify-between py-2.5">
                <dt className="text-muted">{BOOKING_STATUS_LABEL[k]}</dt>
                <dd className="tabular-nums">{s.bookingsByStatus[k] ?? 0}</dd>
              </div>
            ))}
          </dl>
        </Section>
      </div>
      <p className="mt-8 text-[13px] text-faint">
        {s.totalUsers} users in total ({s.deactivatedUsers} deactivated) · {s.totalVehicles} vehicles · {s.totalBookings} bookings ·{' '}
        {s.totalReviews} reviews
      </p>
    </>
  );
}
