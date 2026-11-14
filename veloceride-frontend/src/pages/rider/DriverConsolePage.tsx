import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, LocateFixed, LocateOff, Phone, Play } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { rideService } from '@/services/rideService';
import { bookingService } from '@/services/bookingService';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/toastStore';
import { useRideTracking } from '@/hooks/useRideTracking';
import { useLocationSharing } from '@/hooks/useLocationSharing';
import { useNow } from '@/hooks/useNow';
import { PageHeader, Section } from '@/components/layout/PageHeader';
import { EmptyState, ErrorState, Notice, PageLoader } from '@/components/common/Feedback';
import { RideStatusBadge } from '@/components/common/StatusBadge';
import { Button, buttonClass } from '@/components/common/Button';
import { Avatar } from '@/components/common/Avatar';
import { ConfirmDialog } from '@/components/modals/ConfirmDialog';
import { MapView, ridePoints, type MapPoint } from '@/components/maps/MapView';
import { ConnectionPill } from '@/components/maps/LiveStatus';
import { MAX_PAGE_SIZE } from '@/constants/config';
import { formatDate, formatTime, plural, timeAgo } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

/** Page 24: start → share GPS → complete, following the backend's ride lifecycle rules. */
export default function DriverConsolePage() {
  const { rideId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const now = useNow(2_000);
  const [confirm, setConfirm] = useState<'start' | 'complete' | null>(null);

  const rideQuery = useQuery({ queryKey: qk.ride(rideId), queryFn: () => rideService.get(rideId) });
  const ride = rideQuery.data;
  const isOwner = Boolean(ride && user && ride.driver.id === user.id);
  const watchable = ride?.rideStatus === 'SCHEDULED' || ride?.rideStatus === 'ONGOING';
  const tracking = useRideTracking(rideId, isOwner && watchable, false);
  const status = tracking.pushedStatus ?? ride?.rideStatus;
  const sharing = useLocationSharing(rideId, status === 'ONGOING');

  const passengers = useQuery({
    queryKey: qk.received({ rideId, status: 'ACCEPTED' }),
    queryFn: () => bookingService.received({ rideId, status: 'ACCEPTED', size: MAX_PAGE_SIZE }),
    enabled: isOwner && status !== 'CANCELLED',
  });

  useEffect(() => {
    if (tracking.pushedStatus && tracking.pushedStatus !== ride?.rideStatus) {
      void queryClient.invalidateQueries({ queryKey: qk.ride(rideId) });
    }
  }, [tracking.pushedStatus, ride?.rideStatus, queryClient, rideId]);

  const lifecycle = useMutation({
    mutationFn: (kind: 'start' | 'complete') => (kind === 'start' ? rideService.start(rideId) : rideService.complete(rideId)),
    onSuccess: (updated, kind) => {
      queryClient.setQueryData(qk.ride(rideId), updated);
      void queryClient.invalidateQueries({ queryKey: qk.rides });
      void queryClient.invalidateQueries({ queryKey: qk.bookings });
      setConfirm(null);
      if (kind === 'start') {
        toast.success('Ride started', 'Share your location so passengers can follow you.');
        sharing.start();
      } else {
        sharing.stop();
        toast.success('Ride completed', 'Rate your passengers while it’s fresh.');
        navigate(`/my-rides/${rideId}`, { replace: true });
      }
    },
    onError: (err) => {
      toast.error('Action failed', getErrorMessage(err));
      setConfirm(null);
    },
  });

  if (rideQuery.isPending) return <PageLoader />;
  if (rideQuery.isError) return <ErrorState error={rideQuery.error} onRetry={() => rideQuery.refetch()} />;
  const r = ride!;
  if (!isOwner) return <Navigate to={`/rides/${rideId}`} replace />;

  const header = (
    <PageHeader
      back={{ to: `/my-rides/${rideId}`, label: 'Back to ride' }}
      title={`${r.source} → ${r.destination}`}
      description={`${formatDate(r.departureDate)} · ${formatTime(r.departureTime)}`}
      actions={status && <RideStatusBadge status={status} />}
    />
  );

  if (status === 'COMPLETED' || status === 'CANCELLED') {
    return (
      <>
        {header}
        <EmptyState
          icon={<CheckCircle2 className="size-8" aria-hidden />}
          title={status === 'COMPLETED' ? 'Ride completed' : 'Ride cancelled'}
          description="Live tracking has ended."
          action={
            <Link to={`/my-rides/${rideId}`} className={buttonClass('primary', 'sm')}>
              {status === 'COMPLETED' ? 'Rate passengers' : 'Back to ride'}
            </Link>
          }
        />
      </>
    );
  }

  const me: MapPoint | null = sharing.position
    ? { lat: sharing.position.latitude, lng: sharing.position.longitude, label: 'You', kind: 'vehicle' }
    : null;
  const points = [...ridePoints(r), ...(me ? [me] : [])];
  const isSharing = sharing.state === 'sharing' || sharing.state === 'requesting';

  return (
    <>
      {header}
      <div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
        <div className="min-w-0 space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <ConnectionPill />
            {status === 'ONGOING' && (
              <span className="text-[13px] text-muted">
                {sharing.state === 'sharing'
                  ? sharing.lastSentAt
                    ? `Location sent ${timeAgo(new Date(sharing.lastSentAt).toISOString(), now)}`
                    : 'Getting your position…'
                  : sharing.state === 'requesting'
                    ? 'Waiting for location permission…'
                    : 'Location sharing is off'}
                {sharing.position && sharing.state === 'sharing' && ` · ±${Math.round(sharing.position.accuracy)} m`}
              </span>
            )}
          </div>
          {sharing.error && <Notice tone="danger">{sharing.error}</Notice>}
          {status === 'SCHEDULED' && (
            <Notice>
              Start the ride when you set off. Pending requests will be declined and confirmed passengers notified.
            </Notice>
          )}
          {points.length > 0 ? (
            <MapView points={points} follow={me} className="h-[55vh] min-h-80" />
          ) : (
            <EmptyState title="Your position will appear here" description="Turn on location sharing to show it on the map." />
          )}
        </div>

        <aside className="space-y-6 lg:sticky lg:top-24 lg:self-start">
          <div className="space-y-2">
            {status === 'SCHEDULED' && (
              <Button block size="lg" icon={<Play className="size-4" />} onClick={() => setConfirm('start')}>
                Start ride
              </Button>
            )}
            {status === 'ONGOING' && (
              <>
                {isSharing ? (
                  <Button block variant="secondary" icon={<LocateOff className="size-4" />} onClick={sharing.stop}>
                    Pause location sharing
                  </Button>
                ) : (
                  <Button block size="lg" icon={<LocateFixed className="size-4" />} onClick={sharing.start}>
                    Share my location
                  </Button>
                )}
                <Button block variant="secondary" icon={<CheckCircle2 className="size-4" />} onClick={() => setConfirm('complete')}>
                  Complete ride
                </Button>
              </>
            )}
          </div>

          <Section title={`Passengers${passengers.data ? ` · ${passengers.data.totalElements}` : ''}`}>
            {passengers.data?.content.length ? (
              <ul className="space-y-3">
                {passengers.data.content.map((b) => (
                  <li key={b.id} className="flex items-center gap-3">
                    <Avatar name={b.passenger.fullName} src={b.passenger.profilePicture} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{b.passenger.fullName}</p>
                      <p className="text-[13px] text-muted">{plural(b.numberOfSeats, 'seat')}</p>
                    </div>
                    {b.contactPhone && (
                      <a href={`tel:${b.contactPhone}`} aria-label={`Call ${b.passenger.fullName}`} className="rounded-lg p-2 text-muted hover:bg-surface hover:text-ink">
                        <Phone className="size-4" />
                      </a>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-[13px] text-muted">{passengers.isPending ? 'Loading…' : 'No confirmed passengers.'}</p>
            )}
          </Section>
        </aside>
      </div>

      <ConfirmDialog
        open={confirm === 'start'}
        title="Start this ride?"
        description="Passengers will be notified and can follow your location once you share it."
        confirmLabel="Start ride"
        cancelLabel="Not yet"
        loading={lifecycle.isPending}
        onConfirm={() => lifecycle.mutate('start')}
        onClose={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm === 'complete'}
        title="Complete this ride?"
        description="Location sharing stops, bookings are marked completed and everyone can leave reviews."
        confirmLabel="Complete ride"
        cancelLabel="Not yet"
        loading={lifecycle.isPending}
        onConfirm={() => lifecycle.mutate('complete')}
        onClose={() => setConfirm(null)}
      />
    </>
  );
}
