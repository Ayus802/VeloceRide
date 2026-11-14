import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Car, Search, Ticket } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { rideService } from '@/services/rideService';
import { bookingService } from '@/services/bookingService';
import { notificationService } from '@/services/notificationService';
import { useAuthStore } from '@/store/authStore';
import { useMode } from '@/hooks/useMode';
import { useUpcomingBookings } from '@/hooks/queries';
import { Section } from '@/components/layout/PageHeader';
import { ErrorState } from '@/components/common/Feedback';
import { BookingStatusBadge, RideStatusBadge } from '@/components/common/StatusBadge';
import { RouteTimeline } from '@/components/rides/RideBits';
import { BookingRow } from '@/components/bookings/BookingRow';
import { RequestActions } from '@/components/bookings/RequestActions';
import { buttonClass } from '@/components/common/Button';
import { notificationTarget } from '@/utils/notificationTarget';
import { formatDate, plural, timeAgo } from '@/utils/format';
import { cn } from '@/utils/cn';

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

const cardBase = 'rounded-xl border border-line bg-bg-2 p-5 shadow-card';

/** A journey with real data: whole card links to it. */
function JourneyCard({ to, label, meta, children }: { to: string; label: ReactNode; meta: ReactNode; children: ReactNode }) {
  return (
    <Link to={to} className={cn(cardBase, 'group block transition-colors hover:border-line-strong')}>
      <div className="mb-4 flex items-center justify-between gap-3 text-[13px] text-muted">
        <span className="truncate">{label}</span>
        {meta}
      </div>
      {children}
      <span className="mt-4 inline-flex items-center gap-1 text-[13px] font-medium text-accent">
        View details <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" aria-hidden />
      </span>
    </Link>
  );
}

/** Intentional empty state: icon, one line of context, one quiet link. */
function JourneyEmpty({ icon, title, text, link }: { icon: ReactNode; title: string; text: string; link: { to: string; label: string } }) {
  return (
    <div className={cn(cardBase, 'flex items-start gap-4')}>
      <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface text-muted [&>svg]:size-5">{icon}</span>
      <div className="min-w-0">
        <p className="font-medium text-ink">{title}</p>
        <p className="mt-0.5 text-muted">{text}</p>
        <Link to={link.to} className="mt-3 inline-flex items-center gap-1 text-[13px] font-medium text-accent hover:underline">
          {link.label} <ArrowRight className="size-3.5" aria-hidden />
        </Link>
      </div>
    </div>
  );
}

function CardSkeleton() {
  return (
    <div className={cn(cardBase, 'space-y-3')} aria-hidden>
      <div className="h-3 w-1/3 animate-pulse rounded bg-surface" />
      <div className="h-4 w-2/3 animate-pulse rounded bg-surface" />
      <div className="h-4 w-1/2 animate-pulse rounded bg-surface" />
    </div>
  );
}

function RecentActivity() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: qk.notificationList({ size: 5 }),
    queryFn: () => notificationService.list({ size: 5 }),
  });
  const items = query.data?.content ?? [];
  // Only real notifications are shown; with none (or on error) the section is omitted.
  if (!items.length) return null;
  return (
    <Section
      title="Recent activity"
      action={
        <Link to="/notifications" className="text-[13px] font-medium text-accent hover:underline">
          View all
        </Link>
      }
    >
      <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
        {items.map((n) => (
          <li key={n.id}>
            <button
              type="button"
              onClick={async () => {
                if (!n.isRead) {
                  notificationService
                    .markRead(n.id)
                    .then(() => queryClient.invalidateQueries({ queryKey: qk.notifications }))
                    .catch(() => {});
                }
                const target = await notificationTarget(n).catch(() => null);
                navigate(target ?? '/notifications');
              }}
              className="flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-surface sm:px-5"
            >
              <span className={cn('mt-1.5 size-2 shrink-0 rounded-full', n.isRead ? 'bg-line-strong' : 'bg-accent-bright')} aria-hidden />
              <span className="min-w-0 flex-1">
                <span className={cn('block truncate', n.isRead ? 'text-muted' : 'font-medium text-ink')}>{n.title}</span>
                <span className="block truncate text-[13px] text-muted">{n.message}</span>
              </span>
              <span className="shrink-0 text-xs text-faint">{timeAgo(n.createdAt)}</span>
            </button>
          </li>
        ))}
      </ul>
    </Section>
  );
}

/** Page 05: what needs attention — next trip, next drive, waiting requests, recent activity. */
export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const { isRider } = useMode();
  const upcoming = useUpcomingBookings();
  const nextBooking = upcoming.bookings[0];

  const ongoingRide = useQuery({
    queryKey: qk.myRides({ status: 'ONGOING', size: 1 }),
    queryFn: () => rideService.mine({ status: 'ONGOING', size: 1 }),
  });
  const nextRide = useQuery({
    queryKey: qk.myRides({ status: 'SCHEDULED', size: 1 }),
    queryFn: () => rideService.mine({ status: 'SCHEDULED', size: 1, sort: ['departureDate,asc', 'departureTime,asc'] }),
  });
  // Any ride ever published (any status) — decides between "first ride" and "next ride" wording.
  const anyRide = useQuery({
    queryKey: qk.myRides({ any: true, size: 1 }),
    queryFn: () => rideService.mine({ size: 1 }),
  });
  const requests = useQuery({
    queryKey: qk.received({ status: 'PENDING', size: 3 }),
    queryFn: () => bookingService.received({ status: 'PENDING', size: 3, sort: 'createdAt,asc' }),
  });

  const drive = ongoingRide.data?.content[0] ?? nextRide.data?.content[0];
  const firstName = user?.fullName.split(' ')[0] ?? '';
  const neverPublished = anyRide.data?.totalElements === 0;

  const tripCard = (
    <div>
      <h2 className="mb-3 text-[13px] font-medium text-muted">Your next trip</h2>
      {upcoming.isPending ? (
        <CardSkeleton />
      ) : upcoming.error ? (
        <ErrorState className={cardBase} error={upcoming.error} onRetry={upcoming.refetch} />
      ) : nextBooking ? (
        <JourneyCard
          to={`/bookings/${nextBooking.id}`}
          label={`${formatDate(nextBooking.ride.departureDate)} · with ${nextBooking.driver.fullName}`}
          meta={<BookingStatusBadge status={nextBooking.bookingStatus} />}
        >
          <RouteTimeline source={nextBooking.ride.source} destination={nextBooking.ride.destination} departureTime={nextBooking.ride.departureTime} compact />
        </JourneyCard>
      ) : (
        <JourneyEmpty
          icon={<Ticket />}
          title="No upcoming trips"
          text="Find a ride and your upcoming journey will appear here."
          link={{ to: '/rides/search', label: 'Explore rides' }}
        />
      )}
    </div>
  );

  const drivePending = nextRide.isPending || ongoingRide.isPending || anyRide.isPending;
  const driveCard = (
    <div>
      <h2 className="mb-3 text-[13px] font-medium text-muted">{drive?.rideStatus === 'ONGOING' ? 'On the road now' : 'Your next drive'}</h2>
      {drive ? (
        <JourneyCard
          to={drive.rideStatus === 'ONGOING' ? `/my-rides/${drive.id}/live` : `/my-rides/${drive.id}`}
          label={`${formatDate(drive.departureDate)} · ${drive.bookedSeats} of ${plural(drive.totalSeats, 'seat')} booked`}
          meta={<RideStatusBadge status={drive.rideStatus} />}
        >
          <RouteTimeline source={drive.source} destination={drive.destination} departureTime={drive.departureTime} compact />
        </JourneyCard>
      ) : drivePending ? (
        <CardSkeleton />
      ) : nextRide.isError ? (
        <ErrorState className={cardBase} error={nextRide.error} onRetry={() => nextRide.refetch()} />
      ) : neverPublished ? (
        <JourneyEmpty
          icon={<Car />}
          title="You haven’t published a ride yet."
          text="Your published journeys will appear here."
          link={{ to: '/rides/new', label: 'Publish your first ride' }}
        />
      ) : (
        <JourneyEmpty
          icon={<Car />}
          title="No upcoming rides to drive"
          text="Your next published journey will appear here."
          link={{ to: '/my-rides', label: 'View your rides' }}
        />
      )}
    </div>
  );

  return (
    <>
      <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            {greeting()}, {firstName}
          </h1>
          <p className="mt-1 text-muted">{isRider ? 'Here’s what’s happening with your rides.' : 'Here’s what’s coming up.'}</p>
        </div>
        <div className="flex gap-2">
          <Link to="/rides/search" className={buttonClass('primary', 'md', 'flex-1 sm:flex-none')}>
            <Search className="size-4" aria-hidden /> Find a ride
          </Link>
          <Link to="/rides/new" className={buttonClass('secondary', 'md', 'flex-1 sm:flex-none')}>
            Publish a ride
          </Link>
        </div>
      </header>

      <div className="grid gap-6 md:grid-cols-2">
        {isRider ? (
          <>
            {driveCard}
            {tripCard}
          </>
        ) : (
          <>
            {tripCard}
            {driveCard}
          </>
        )}
      </div>

      {(requests.data?.totalElements ?? 0) > 0 && (
        <Section
          title="Requests waiting for you"
          action={
            requests.data!.totalElements > 3 && (
              <Link to="/requests" className="text-[13px] font-medium text-accent hover:underline">
                See all {requests.data!.totalElements}
              </Link>
            )
          }
        >
          <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
            {requests.data!.content.map((b) => (
              <BookingRow key={b.id} booking={b} perspective="driver" actions={<RequestActions booking={b} />} />
            ))}
          </ul>
        </Section>
      )}

      <RecentActivity />
    </>
  );
}
