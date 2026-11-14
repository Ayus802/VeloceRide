import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ban, Check, Navigation, Phone, Route, X } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { bookingService } from '@/services/bookingService';
import { rideService } from '@/services/rideService';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/toastStore';
import { PageHeader, Panel, DetailList, Section } from '@/components/layout/PageHeader';
import { ErrorState, Notice, PageLoader } from '@/components/common/Feedback';
import { BookingStatusBadge, RideStatusBadge } from '@/components/common/StatusBadge';
import { Avatar } from '@/components/common/Avatar';
import { Button, buttonClass } from '@/components/common/Button';
import { ActionsMenu, DropdownItem } from '@/components/dropdowns/Dropdown';
import { ConfirmDialog } from '@/components/modals/ConfirmDialog';
import { ReviewModal } from '@/components/modals/ReviewModal';
import { PaymentPanel } from '@/components/bookings/PaymentPanel';
import { formatDate, formatInstant, formatMoney, formatTime, plural } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

type Pending = 'cancel' | 'accept' | 'reject' | null;

export default function BookingDetailsPage() {
  const { bookingId = '' } = useParams();
  const user = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState<Pending>(null);
  const [reviewOpen, setReviewOpen] = useState(false);

  const query = useQuery({ queryKey: qk.booking(bookingId), queryFn: () => bookingService.get(bookingId) });
  const b = query.data;
  const isPassenger = Boolean(b && user && b.passenger.id === user.id);
  const isDriver = Boolean(b && user && b.driver.id === user.id);

  const reviewsQuery = useQuery({
    queryKey: qk.rideReviews(b?.ride.id ?? ''),
    queryFn: () => rideService.reviews(b!.ride.id),
    enabled: Boolean(b && b.bookingStatus === 'COMPLETED' && (isPassenger || isDriver)),
  });

  const action = useMutation({
    mutationFn: (kind: Exclude<Pending, null>) =>
      kind === 'cancel' ? bookingService.cancel(bookingId) : kind === 'accept' ? bookingService.accept(bookingId) : bookingService.reject(bookingId),
    onSuccess: (updated, kind) => {
      queryClient.setQueryData(qk.booking(bookingId), updated);
      void queryClient.invalidateQueries({ queryKey: qk.bookings });
      void queryClient.invalidateQueries({ queryKey: qk.rides });
      void queryClient.invalidateQueries({ queryKey: qk.payments });
      toast.success(kind === 'cancel' ? 'Booking cancelled' : kind === 'accept' ? 'Booking accepted' : 'Booking declined');
      setConfirming(null);
    },
    onError: (err) => {
      toast.error('Action failed', getErrorMessage(err));
      setConfirming(null);
      void query.refetch();
    },
  });

  if (query.isPending) return <PageLoader />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const booking = b!;
  const other = isPassenger ? booking.driver : booking.passenger;
  const rideStatus = booking.ride.rideStatus;

  const canCancel = isPassenger && (booking.bookingStatus === 'PENDING' || booking.bookingStatus === 'ACCEPTED') && rideStatus === 'SCHEDULED';
  const canDecide = isDriver && booking.bookingStatus === 'PENDING' && rideStatus === 'SCHEDULED';
  const canTrack = isPassenger && booking.bookingStatus === 'ACCEPTED' && (rideStatus === 'ONGOING' || rideStatus === 'SCHEDULED');
  const reviewee = isPassenger ? booking.driver : booking.passenger;
  const alreadyReviewed = reviewsQuery.data?.some((r) => r.reviewer.id === user?.id && r.reviewedUserId === reviewee.id);
  const canReview = booking.bookingStatus === 'COMPLETED' && reviewsQuery.isSuccess && !alreadyReviewed;

  return (
    <>
      <PageHeader
        back={isDriver ? { to: `/my-rides/${booking.ride.id}`, label: 'Back to ride' } : { to: '/bookings', label: 'My trips' }}
        title={`${booking.ride.source} → ${booking.ride.destination}`}
        description={
          <span className="inline-flex flex-wrap items-center gap-2">
            <span>Booking {booking.bookingReference}</span>
            <BookingStatusBadge status={booking.bookingStatus} />
          </span>
        }
        actions={
          <ActionsMenu>
            <DropdownItem to={`/rides/${booking.ride.id}`} icon={<Route />}>
              View ride
            </DropdownItem>
            {canCancel && (
              <DropdownItem tone="danger" icon={<Ban />} onSelect={() => setConfirming('cancel')}>
                Cancel booking
              </DropdownItem>
            )}
          </ActionsMenu>
        }
      />

      <div className="max-w-2xl">
        {canDecide && (
          <Notice tone="warning" className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <span>{booking.passenger.fullName} is waiting for your answer.</span>
            <span className="flex gap-2">
              <Button size="sm" variant="secondary" icon={<X className="size-4" />} onClick={() => setConfirming('reject')}>
                Decline
              </Button>
              <Button size="sm" icon={<Check className="size-4" />} onClick={() => setConfirming('accept')}>
                Accept
              </Button>
            </span>
          </Notice>
        )}

        {canTrack && rideStatus === 'ONGOING' && (
          <Link to={`/rides/${booking.ride.id}/track?booking=${booking.id}`} className={buttonClass('primary', 'lg', 'mb-6 w-full sm:w-auto')}>
            <Navigation className="size-4" aria-hidden /> Track your driver live
          </Link>
        )}
        {canTrack && rideStatus === 'SCHEDULED' && (
          <p className="mb-6 text-[13px] text-muted">
            Live tracking becomes available once {booking.driver.fullName} starts the ride.{' '}
            <Link to={`/rides/${booking.ride.id}/track?booking=${booking.id}`} className="text-accent hover:underline">
              Open tracking page
            </Link>
          </p>
        )}
        {booking.bookingStatus === 'PENDING' && isPassenger && (
          <Notice className="mb-6">Waiting for {booking.driver.fullName} to accept. You’ll be notified as soon as they answer.</Notice>
        )}

        <Panel>
          <DetailList
            items={[
              { label: 'Date', value: formatDate(booking.ride.departureDate, { year: true }) },
              { label: 'Departure', value: formatTime(booking.ride.departureTime) },
              { label: 'Ride status', value: <RideStatusBadge status={rideStatus} /> },
              { label: 'Seats', value: plural(booking.numberOfSeats, 'seat') },
              { label: 'Price per seat', value: formatMoney(booking.ride.pricePerSeat) },
              { label: 'Total', value: <span className="font-semibold">{formatMoney(booking.totalAmount)}</span> },
              { label: 'Requested', value: formatInstant(booking.createdAt) },
            ]}
          />
        </Panel>

        <Section title={isPassenger ? 'Driver' : 'Passenger'}>
          <div className="flex flex-wrap items-center gap-4">
            <Avatar name={other.fullName} src={other.profilePicture} />
            <div className="min-w-0 flex-1">
              <Link to={`/users/${other.id}`} className="font-medium hover:text-accent">
                {other.fullName}
              </Link>
              {booking.contactPhone ? (
                <a href={`tel:${booking.contactPhone}`} className="mt-0.5 flex items-center gap-1.5 text-[13px] text-muted hover:text-ink">
                  <Phone className="size-3.5" aria-hidden />
                  {booking.contactPhone}
                </a>
              ) : (
                <p className="mt-0.5 text-[13px] text-faint">Phone number is shared once the booking is confirmed.</p>
              )}
            </div>
            {canReview && (
              <Button variant="secondary" size="sm" onClick={() => setReviewOpen(true)}>
                Rate {isPassenger ? 'driver' : 'passenger'}
              </Button>
            )}
          </div>
        </Section>

        {isPassenger && <PaymentPanel booking={booking} />}
      </div>

      <ConfirmDialog
        open={confirming === 'cancel'}
        title="Cancel this booking?"
        description={
          booking.bookingStatus === 'ACCEPTED'
            ? 'Your seats will be released and any successful payment refunded.'
            : 'Your request will be withdrawn.'
        }
        confirmLabel="Cancel booking"
        tone="danger"
        loading={action.isPending}
        onConfirm={() => action.mutate('cancel')}
        onClose={() => setConfirming(null)}
      />
      <ConfirmDialog
        open={confirming === 'accept'}
        title={`Accept ${booking.passenger.fullName}?`}
        description={`${plural(booking.numberOfSeats, 'seat')} will be reserved for ${formatMoney(booking.totalAmount)}. Your phone numbers will be shared with each other.`}
        confirmLabel="Accept"
        cancelLabel="Back"
        loading={action.isPending}
        onConfirm={() => action.mutate('accept')}
        onClose={() => setConfirming(null)}
      />
      <ConfirmDialog
        open={confirming === 'reject'}
        title={`Decline ${booking.passenger.fullName}?`}
        description="They’ll be notified that you declined the request."
        confirmLabel="Decline"
        cancelLabel="Back"
        tone="danger"
        loading={action.isPending}
        onConfirm={() => action.mutate('reject')}
        onClose={() => setConfirming(null)}
      />
      {reviewOpen && (
        <ReviewModal
          open
          onClose={() => setReviewOpen(false)}
          rideId={booking.ride.id}
          reviewee={reviewee}
          role={isPassenger ? 'driver' : 'passenger'}
        />
      )}
    </>
  );
}
