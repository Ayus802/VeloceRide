import { useEffect } from 'react';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock, MapPinOff } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { rideService } from '@/services/rideService';
import { bookingService } from '@/services/bookingService';
import { locationService } from '@/services/locationService';
import { useAuthStore } from '@/store/authStore';
import { useRideTracking } from '@/hooks/useRideTracking';
import { useNow } from '@/hooks/useNow';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState, ErrorState, Notice, PageLoader } from '@/components/common/Feedback';
import { RideStatusBadge } from '@/components/common/StatusBadge';
import { buttonClass } from '@/components/common/Button';
import { MapView, ridePoints, type MapPoint } from '@/components/maps/MapView';
import { ConnectionPill } from '@/components/maps/LiveStatus';
import { LOCATION_STALE_MS } from '@/constants/config';
import { errorStatus } from '@/utils/errors';
import { formatDate, formatTime, timeAgo } from '@/utils/format';

/**
 * Passenger live tracking (page 17). Eligibility is confirmed through REST before subscribing,
 * because the server closes the STOMP connection on an unauthorized SUBSCRIBE.
 */
export default function TrackRidePage() {
  const { rideId = '' } = useParams();
  const [params] = useSearchParams();
  const bookingId = params.get('booking');
  const user = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const now = useNow(5_000);

  const rideQuery = useQuery({ queryKey: qk.ride(rideId), queryFn: () => rideService.get(rideId) });
  const ride = rideQuery.data;
  const isDriver = Boolean(ride && user && ride.driver.id === user.id);

  const bookingQuery = useQuery({
    queryKey: qk.booking(bookingId ?? ''),
    queryFn: () => bookingService.get(bookingId!),
    enabled: Boolean(bookingId),
  });
  // Without a booking id, an ONGOING ride's "latest" endpoint tells us if we may watch (403 = no).
  const probe = useQuery({
    queryKey: ['location-probe', rideId],
    queryFn: () => locationService.latest(rideId),
    enabled: !bookingId && ride?.rideStatus === 'ONGOING' && !isDriver,
    retry: false,
  });

  const viaBooking =
    bookingQuery.data &&
    bookingQuery.data.ride.id === rideId &&
    bookingQuery.data.passenger.id === user?.id &&
    bookingQuery.data.bookingStatus === 'ACCEPTED';
  const viaProbe = probe.isSuccess || errorStatus(probe.error) === 404;
  const eligible = Boolean(viaBooking || viaProbe);
  const watchable = ride?.rideStatus === 'SCHEDULED' || ride?.rideStatus === 'ONGOING';

  const tracking = useRideTracking(rideId, eligible && watchable, ride?.rideStatus === 'ONGOING');
  const status = tracking.pushedStatus ?? ride?.rideStatus;

  useEffect(() => {
    if (!tracking.pushedStatus) return;
    void queryClient.invalidateQueries({ queryKey: qk.ride(rideId) });
    void queryClient.invalidateQueries({ queryKey: qk.bookings });
  }, [tracking.pushedStatus, queryClient, rideId]);

  if (rideQuery.isPending || (bookingId && bookingQuery.isPending) || probe.isFetching) return <PageLoader />;
  if (rideQuery.isError) return <ErrorState error={rideQuery.error} onRetry={() => rideQuery.refetch()} />;
  if (isDriver) return <Navigate to={`/my-rides/${rideId}/live`} replace />;
  const r = ride!;

  const header = (
    <PageHeader
      back={bookingId ? { to: `/bookings/${bookingId}`, label: 'Back to booking' } : { to: '/bookings', label: 'My trips' }}
      title={`${r.source} → ${r.destination}`}
      description={`${formatDate(r.departureDate)} · ${formatTime(r.departureTime)} · ${r.driver.fullName}`}
      actions={status && <RideStatusBadge status={status} />}
    />
  );

  if (!eligible || tracking.denied) {
    return (
      <>
        {header}
        <EmptyState
          icon={<MapPinOff className="size-8" aria-hidden />}
          title="Live tracking isn’t available"
          description={
            tracking.denied
              ? 'You’re not allowed to follow this ride.'
              : r.rideStatus === 'SCHEDULED'
                ? 'Only passengers with a confirmed booking can follow this ride. Open it from your booking.'
                : 'Only the driver and confirmed passengers can follow this ride while it’s on the road.'
          }
          action={
            <Link to="/bookings" className={buttonClass('secondary', 'sm')}>
              My trips
            </Link>
          }
        />
      </>
    );
  }

  const ended = status === 'COMPLETED' || status === 'CANCELLED';
  const loc = tracking.location;
  const stale = loc ? now - new Date(loc.receivedAt).getTime() > LOCATION_STALE_MS : false;
  const vehicle: MapPoint | null = loc ? { lat: loc.latitude, lng: loc.longitude, label: r.driver.fullName, kind: 'vehicle' } : null;
  const points = [...ridePoints(r), ...(vehicle ? [vehicle] : [])];

  return (
    <>
      {header}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        {!ended && <ConnectionPill />}
        {loc && !ended && (
          <span className={stale ? 'inline-flex items-center gap-1.5 text-[13px] text-warning' : 'inline-flex items-center gap-1.5 text-[13px] text-muted'}>
            <Clock className="size-3.5" aria-hidden />
            Last update {timeAgo(loc.receivedAt, now)}
            {stale && ' · signal may be delayed'}
          </span>
        )}
      </div>

      {ended ? (
        <EmptyState
          title={status === 'COMPLETED' ? 'You’ve arrived' : 'This ride was cancelled'}
          description={status === 'COMPLETED' ? 'Tracking has ended. Don’t forget to rate your driver.' : 'Tracking has ended.'}
          action={
            bookingId && (
              <Link to={`/bookings/${bookingId}`} className={buttonClass('primary', 'sm')}>
                {status === 'COMPLETED' ? 'Rate your driver' : 'View booking'}
              </Link>
            )
          }
        />
      ) : (
        <>
          {status === 'SCHEDULED' && (
            <Notice className="mb-4">
              {r.driver.fullName} hasn’t started the ride yet. This page updates automatically when they do.
            </Notice>
          )}
          {status === 'ONGOING' && !loc && <Notice className="mb-4">Waiting for the driver’s first location update…</Notice>}
          {points.length > 0 ? (
            <MapView points={points} follow={vehicle} className="h-[60vh] min-h-80" />
          ) : (
            <EmptyState title="Map will appear here" description="The driver’s position shows up once they start sharing their location." />
          )}
        </>
      )}
    </>
  );
}
