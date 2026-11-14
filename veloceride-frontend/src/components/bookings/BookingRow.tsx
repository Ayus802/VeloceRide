import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import type { Booking } from '@/types/dto';
import { Avatar } from '@/components/common/Avatar';
import { BookingStatusBadge } from '@/components/common/StatusBadge';
import { formatDate, formatMoney, formatTime, plural } from '@/utils/format';

/**
 * Compact booking row. `perspective` decides whose name is shown:
 * passengers see the driver, drivers see the passenger.
 */
export function BookingRow({
  booking,
  perspective,
  actions,
  hideRoute,
}: {
  booking: Booking;
  perspective: 'passenger' | 'driver';
  actions?: ReactNode;
  hideRoute?: boolean;
}) {
  const other = perspective === 'passenger' ? booking.driver : booking.passenger;
  return (
    <li className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:gap-5 sm:px-5">
      <Link to={`/bookings/${booking.id}`} className="group flex min-w-0 flex-1 items-center gap-4">
        <Avatar name={other.fullName} src={other.profilePicture} />
        <div className="min-w-0 flex-1">
          {!hideRoute && (
            <p className="flex items-center gap-1.5 truncate font-medium text-ink">
              <span className="truncate">{booking.ride.source}</span>
              <ArrowRight className="size-3.5 shrink-0 text-faint" aria-hidden />
              <span className="truncate">{booking.ride.destination}</span>
            </p>
          )}
          <p className="mt-0.5 truncate text-[13px] text-muted">
            {hideRoute ? <span className="text-ink">{other.fullName}</span> : formatDate(booking.ride.departureDate)}
            {!hideRoute && ` · ${formatTime(booking.ride.departureTime)}`}
            {` · ${plural(booking.numberOfSeats, 'seat')} · ${formatMoney(booking.totalAmount)}`}
            {!hideRoute && ` · ${perspective === 'passenger' ? 'Driver' : 'Passenger'}: ${other.fullName}`}
          </p>
        </div>
        <BookingStatusBadge status={booking.bookingStatus} />
      </Link>
      {actions && <div className="flex shrink-0 items-center gap-2 sm:justify-end">{actions}</div>}
    </li>
  );
}
