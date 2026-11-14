import { Link } from 'react-router-dom';
import { ChevronRight, Users } from 'lucide-react';
import type { Ride } from '@/types/dto';
import { Avatar } from '@/components/common/Avatar';
import { RatingInline } from '@/components/common/Rating';
import { RideStatusBadge } from '@/components/common/StatusBadge';
import { VEHICLE_TYPE_LABEL } from '@/constants/labels';
import { formatDate, formatMoney, formatTime, plural } from '@/utils/format';
import { cn } from '@/utils/cn';

/** Vertical "time · place" timeline for a route. */
export function RouteTimeline({
  source,
  destination,
  departureTime,
  arrival,
  compact,
}: {
  source: string;
  destination: string;
  departureTime?: string;
  arrival?: string;
  compact?: boolean;
}) {
  return (
    <ol className={cn('relative grid gap-3', compact ? 'text-sm' : 'text-[15px]')}>
      <span className="absolute bottom-3 left-[59px] top-3 w-px bg-line-strong" aria-hidden />
      <li className="flex items-center gap-3">
        <span className="w-11 shrink-0 text-right font-medium tabular-nums text-ink">{departureTime ? formatTime(departureTime) : ''}</span>
        <span className="relative z-10 size-2.5 shrink-0 rounded-full border-2 border-accent bg-bg" aria-hidden />
        <span className="min-w-0 truncate font-medium text-ink">
          <span className="sr-only">From </span>
          {source}
        </span>
      </li>
      <li className="flex items-center gap-3">
        <span className="w-11 shrink-0 text-right tabular-nums text-muted">{arrival ? formatTime(arrival) : ''}</span>
        <span className="relative z-10 size-2.5 shrink-0 rounded-full bg-accent" aria-hidden />
        <span className="min-w-0 truncate font-medium text-ink">
          <span className="sr-only">To </span>
          {destination}
        </span>
      </li>
    </ol>
  );
}

function arrivalTime(ride: Ride) {
  // Only show arrival time when it falls on the departure date (otherwise it would be misleading).
  if (!ride.estimatedArrivalTime) return undefined;
  const [date, time] = ride.estimatedArrivalTime.split('T');
  return date === ride.departureDate ? time : undefined;
}

/** Search result / list row. Whole row is a link. */
export function RideRow({
  ride,
  to,
  seatsWanted,
  showDate,
  showStatus,
}: {
  ride: Ride;
  to: string;
  seatsWanted?: number;
  showDate?: boolean;
  showStatus?: boolean;
}) {
  const tooFew = seatsWanted != null && ride.availableSeats < seatsWanted;
  return (
    <li>
      <Link
        to={to}
        className={cn(
          'group grid gap-4 px-4 py-4 transition-colors hover:bg-surface/50 sm:grid-cols-[1fr_auto] sm:items-center sm:px-5',
          tooFew && 'opacity-60',
        )}
      >
        <div className="min-w-0">
          {(showDate || showStatus) && (
            <div className="mb-2 flex items-center gap-2 text-[13px] text-muted">
              {showDate && <span>{formatDate(ride.departureDate)}</span>}
              {showStatus && <RideStatusBadge status={ride.rideStatus} />}
            </div>
          )}
          <RouteTimeline
            source={ride.source}
            destination={ride.destination}
            departureTime={ride.departureTime}
            arrival={arrivalTime(ride)}
            compact
          />
          <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 pl-14 text-[13px] text-muted">
            <span className="inline-flex items-center gap-2">
              <Avatar name={ride.driver.fullName} src={ride.driver.profilePicture} size="sm" className="size-6 text-[10px]" />
              <span className="text-ink">{ride.driver.fullName}</span>
              <RatingInline value={ride.driver.averageRating} count={ride.driver.totalReviews || undefined} />
            </span>
            <span>
              {ride.vehicle.brand} {ride.vehicle.model} · {VEHICLE_TYPE_LABEL[ride.vehicle.vehicleType]}
            </span>
          </div>
        </div>
        <div className="flex items-center justify-between gap-6 border-t border-line pt-3 sm:flex-col sm:items-end sm:justify-center sm:border-0 sm:pt-0">
          <span className="text-lg font-semibold text-ink">
            {formatMoney(ride.pricePerSeat)}
            <span className="ml-1 text-[13px] font-normal text-muted">/ seat</span>
          </span>
          <span className={cn('inline-flex items-center gap-1.5 text-[13px]', tooFew ? 'text-warning' : 'text-muted')}>
            <Users className="size-3.5" aria-hidden />
            {tooFew ? `Only ${plural(ride.availableSeats, 'seat')} left` : `${plural(ride.availableSeats, 'seat')} left`}
            <ChevronRight className="hidden size-4 text-faint transition-transform group-hover:translate-x-0.5 sm:block" aria-hidden />
          </span>
        </div>
      </Link>
    </li>
  );
}
