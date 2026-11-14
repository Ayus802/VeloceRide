import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ban, Eye, Navigation, Pencil, Phone, Play, Star } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { rideService } from '@/services/rideService';
import { bookingService } from '@/services/bookingService';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/toastStore';
import { PageHeader, Panel, DetailList, Section } from '@/components/layout/PageHeader';
import { EmptyState, ErrorState, ListSkeleton, PageLoader } from '@/components/common/Feedback';
import { RideStatusBadge } from '@/components/common/StatusBadge';
import { Button, buttonClass } from '@/components/common/Button';
import { Avatar } from '@/components/common/Avatar';
import { ActionsMenu, DropdownItem } from '@/components/dropdowns/Dropdown';
import { ConfirmDialog } from '@/components/modals/ConfirmDialog';
import { ReviewModal } from '@/components/modals/ReviewModal';
import { BookingRow } from '@/components/bookings/BookingRow';
import { RequestActions } from '@/components/bookings/RequestActions';
import { RouteTimeline } from '@/components/rides/RideBits';
import { MAX_PAGE_SIZE } from '@/constants/config';
import { VEHICLE_TYPE_LABEL } from '@/constants/labels';
import type { UserSummary } from '@/types/dto';
import { formatDate, formatLocalDateTime, formatMoney, plural } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

/** Pages 21/23/25: ride overview, requests, passengers and post-ride reviews for the driver. */
export default function ManageRidePage() {
  const { rideId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const [confirm, setConfirm] = useState<'start' | 'cancel' | null>(null);
  const [reviewee, setReviewee] = useState<UserSummary | null>(null);

  const rideQuery = useQuery({ queryKey: qk.ride(rideId), queryFn: () => rideService.get(rideId) });
  const ride = rideQuery.data;
  const isOwner = Boolean(ride && user && ride.driver.id === user.id);
  const passengerStatus = ride?.rideStatus === 'COMPLETED' ? 'COMPLETED' : 'ACCEPTED';

  const requests = useQuery({
    queryKey: qk.received({ rideId, status: 'PENDING' }),
    queryFn: () => bookingService.received({ rideId, status: 'PENDING', size: MAX_PAGE_SIZE, sort: 'createdAt,asc' }),
    enabled: isOwner && ride?.rideStatus === 'SCHEDULED',
  });
  const passengers = useQuery({
    queryKey: qk.received({ rideId, status: passengerStatus }),
    queryFn: () => bookingService.received({ rideId, status: passengerStatus, size: MAX_PAGE_SIZE }),
    enabled: isOwner && ride?.rideStatus !== 'CANCELLED',
  });
  const reviews = useQuery({
    queryKey: qk.rideReviews(rideId),
    queryFn: () => rideService.reviews(rideId),
    enabled: isOwner && ride?.rideStatus === 'COMPLETED',
  });

  const lifecycle = useMutation({
    mutationFn: (kind: 'start' | 'cancel') => (kind === 'start' ? rideService.start(rideId) : rideService.cancel(rideId)),
    onSuccess: (updated, kind) => {
      queryClient.setQueryData(qk.ride(rideId), updated);
      void queryClient.invalidateQueries({ queryKey: qk.rides });
      void queryClient.invalidateQueries({ queryKey: qk.bookings });
      setConfirm(null);
      if (kind === 'start') {
        toast.success('Ride started', 'Passengers have been notified.');
        navigate(`/my-rides/${rideId}/live`);
      } else {
        toast.success('Ride cancelled', 'Passengers have been notified and payments refunded.');
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

  const scheduled = r.rideStatus === 'SCHEDULED';
  const reviewed = new Set((reviews.data ?? []).filter((rv) => rv.reviewer.id === user?.id).map((rv) => rv.reviewedUserId));

  const primary =
    r.rideStatus === 'SCHEDULED' ? (
      <Button icon={<Play className="size-4" />} onClick={() => setConfirm('start')}>
        Start ride
      </Button>
    ) : r.rideStatus === 'ONGOING' ? (
      <Link to={`/my-rides/${rideId}/live`} className={buttonClass('primary')}>
        <Navigation className="size-4" aria-hidden /> Open live console
      </Link>
    ) : null;

  return (
    <>
      <PageHeader
        back={{ to: '/my-rides', label: 'My rides' }}
        title={`${r.source} → ${r.destination}`}
        description={
          <span className="inline-flex flex-wrap items-center gap-2">
            {formatDate(r.departureDate, { year: true })} <RideStatusBadge status={r.rideStatus} />
          </span>
        }
        actions={
          <>
            {primary}
            <ActionsMenu label="Ride actions">
              <DropdownItem to={`/rides/${r.id}`} icon={<Eye />}>
                View as passenger
              </DropdownItem>
              {scheduled && (
                <DropdownItem to={`/my-rides/${r.id}/edit`} icon={<Pencil />}>
                  Edit ride
                </DropdownItem>
              )}
              {scheduled && (
                <DropdownItem tone="danger" icon={<Ban />} onSelect={() => setConfirm('cancel')}>
                  Cancel ride
                </DropdownItem>
              )}
            </ActionsMenu>
          </>
        }
      />

      <div className="grid gap-10 lg:grid-cols-[1fr_20rem]">
        <div className="min-w-0">
          <RouteTimeline source={r.source} destination={r.destination} departureTime={r.departureTime} />

          {scheduled && (
            <Section
              title="Requests"
              description="Seats are only reserved once you accept."
              action={
                (requests.data?.totalElements ?? 0) > 0 && <span className="text-[13px] text-muted">{requests.data!.totalElements} waiting</span>
              }
            >
              {requests.isPending ? (
                <ListSkeleton rows={2} />
              ) : requests.isError ? (
                <ErrorState error={requests.error} onRetry={() => requests.refetch()} />
              ) : requests.data.content.length === 0 ? (
                <p className="rounded-xl border border-line bg-bg-2 px-4 py-6 text-center text-muted">No pending requests.</p>
              ) : (
                <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
                  {requests.data.content.map((b) => (
                    <BookingRow key={b.id} booking={b} perspective="driver" hideRoute actions={<RequestActions booking={b} />} />
                  ))}
                </ul>
              )}
            </Section>
          )}

          {r.rideStatus !== 'CANCELLED' && (
            <Section
              title="Passengers"
              description={r.rideStatus === 'COMPLETED' ? 'Rate the people who travelled with you.' : 'Confirmed passengers on this ride.'}
            >
              {passengers.isPending ? (
                <ListSkeleton rows={2} />
              ) : passengers.isError ? (
                <ErrorState error={passengers.error} onRetry={() => passengers.refetch()} />
              ) : passengers.data.content.length === 0 ? (
                <p className="rounded-xl border border-line bg-bg-2 px-4 py-6 text-center text-muted">No confirmed passengers yet.</p>
              ) : (
                <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
                  {passengers.data.content.map((b) => (
                    <li key={b.id} className="flex flex-wrap items-center gap-4 px-4 py-3 sm:px-5">
                      <Avatar name={b.passenger.fullName} src={b.passenger.profilePicture} />
                      <div className="min-w-0 flex-1">
                        <Link to={`/bookings/${b.id}`} className="font-medium hover:text-accent">
                          {b.passenger.fullName}
                        </Link>
                        <p className="text-[13px] text-muted">
                          {plural(b.numberOfSeats, 'seat')} · {formatMoney(b.totalAmount)} · {b.bookingReference}
                        </p>
                      </div>
                      {b.contactPhone && r.rideStatus !== 'COMPLETED' && (
                        <a href={`tel:${b.contactPhone}`} className="inline-flex items-center gap-1.5 text-[13px] text-muted hover:text-ink">
                          <Phone className="size-3.5" aria-hidden /> {b.contactPhone}
                        </a>
                      )}
                      {r.rideStatus === 'COMPLETED' &&
                        reviews.isSuccess &&
                        (reviewed.has(b.passenger.id) ? (
                          <span className="inline-flex items-center gap-1 text-[13px] text-muted">
                            <Star className="size-3.5 fill-warning text-warning" aria-hidden /> Rated
                          </span>
                        ) : (
                          <Button size="sm" variant="secondary" onClick={() => setReviewee(b.passenger)}>
                            Rate
                          </Button>
                        ))}
                    </li>
                  ))}
                </ul>
              )}
            </Section>
          )}

          {r.rideStatus === 'CANCELLED' && (
            <EmptyState className="mt-6" title="This ride was cancelled" description="All bookings were cancelled and passengers notified." />
          )}
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <Panel>
            <DetailList
              items={[
                { label: 'Seats booked', value: `${r.bookedSeats} of ${r.totalSeats}` },
                { label: 'Price per seat', value: formatMoney(r.pricePerSeat) },
                { label: 'Vehicle', value: `${r.vehicle.brand} ${r.vehicle.model} · ${VEHICLE_TYPE_LABEL[r.vehicle.vehicleType]}` },
                r.estimatedArrivalTime ? { label: 'Arrival', value: formatLocalDateTime(r.estimatedArrivalTime) } : null,
              ]}
            />
            {r.description && <p className="whitespace-pre-line border-t border-line px-4 py-3 text-[13px] text-muted">{r.description}</p>}
          </Panel>
        </aside>
      </div>

      <ConfirmDialog
        open={confirm === 'start'}
        title="Start this ride?"
        description="Pending requests will be declined automatically, confirmed passengers are notified, and live location sharing begins."
        confirmLabel="Start ride"
        cancelLabel="Not yet"
        loading={lifecycle.isPending}
        onConfirm={() => lifecycle.mutate('start')}
        onClose={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm === 'cancel'}
        title="Cancel this ride?"
        description={`All pending and confirmed bookings${r.bookedSeats ? ` (${plural(r.bookedSeats, 'seat')} booked)` : ''} will be cancelled, passengers notified and payments refunded. This can’t be undone.`}
        confirmLabel="Cancel ride"
        tone="danger"
        loading={lifecycle.isPending}
        onConfirm={() => lifecycle.mutate('cancel')}
        onClose={() => setConfirm(null)}
      />
      {reviewee && (
        <ReviewModal open onClose={() => setReviewee(null)} rideId={rideId} reviewee={reviewee} role="passenger" />
      )}
    </>
  );
}
