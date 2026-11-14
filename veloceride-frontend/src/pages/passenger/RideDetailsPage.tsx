import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { BadgeCheck, CalendarDays, Car, Info, Users } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { rideService } from '@/services/rideService';
import { useAuthStore } from '@/store/authStore';
import { Button, buttonClass } from '@/components/common/Button';
import { ErrorState, Notice, PageLoader } from '@/components/common/Feedback';
import { Avatar } from '@/components/common/Avatar';
import { RatingInline } from '@/components/common/Rating';
import { RideStatusBadge } from '@/components/common/StatusBadge';
import { RouteTimeline } from '@/components/rides/RideBits';
import { MapView, ridePoints } from '@/components/maps/MapView';
import { BookingModal } from '@/components/bookings/BookingModal';
import { ReviewModal } from '@/components/modals/ReviewModal';
import { VEHICLE_TYPE_LABEL } from '@/constants/labels';
import { loginRedirect } from '@/constants/routes';
import { formatDate, formatLocalDateTime, formatMoney, plural } from '@/utils/format';

export default function RideDetailsPage() {
  const { rideId = '' } = useParams();
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [bookingOpen, setBookingOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const seatsWanted = Math.max(1, Number(params.get('seats')) || 1);

  const rideQuery = useQuery({ queryKey: qk.ride(rideId), queryFn: () => rideService.get(rideId) });
  const ride = rideQuery.data;
  const isDriver = Boolean(user && ride && ride.driver.id === user.id);

  // Reviews endpoint requires auth; used to know whether I already rated this driver.
  const reviewsQuery = useQuery({
    queryKey: qk.rideReviews(rideId),
    queryFn: () => rideService.reviews(rideId),
    enabled: Boolean(user && ride?.rideStatus === 'COMPLETED' && !isDriver),
  });

  if (rideQuery.isPending) return <PageLoader />;
  if (rideQuery.isError) return <ErrorState className="mt-10" error={rideQuery.error} onRetry={() => rideQuery.refetch()} />;
  const r = ride!;

  const points = ridePoints(r);
  const canBook = r.rideStatus === 'SCHEDULED' && r.availableSeats > 0 && !isDriver;
  const alreadyReviewed = reviewsQuery.data?.some((rv) => rv.reviewer.id === user?.id && rv.reviewedUserId === r.driver.id);
  const showReviewCta = Boolean(user && !isDriver && r.rideStatus === 'COMPLETED' && reviewsQuery.isSuccess && !alreadyReviewed);

  const onBook = () => {
    if (!user) return navigate(loginRedirect(location.pathname, location.search));
    setBookingOpen(true);
  };

  const unavailableReason =
    r.rideStatus !== 'SCHEDULED'
      ? 'This ride is no longer open for booking.'
      : r.availableSeats === 0
        ? 'This ride is full.'
        : null;

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <div className="mb-6 flex flex-wrap items-center gap-3 text-muted">
        <CalendarDays className="size-4" aria-hidden />
        <span className="text-ink">{formatDate(r.departureDate, { year: true })}</span>
        <RideStatusBadge status={r.rideStatus} />
      </div>
      <h1 className="sr-only">
        Ride from {r.source} to {r.destination}
      </h1>

      {isDriver && (
        <Notice className="mb-6">
          This is a ride you’re driving.{' '}
          <Link to={`/my-rides/${r.id}`} className="font-medium text-accent hover:underline">
            Manage ride
          </Link>
        </Notice>
      )}

      <div className="grid gap-10 lg:grid-cols-[1fr_20rem]">
        <div className="min-w-0 space-y-10">
          <RouteTimeline source={r.source} destination={r.destination} departureTime={r.departureTime} />
          {r.estimatedArrivalTime && (
            <p className="-mt-6 pl-14 text-[13px] text-muted">Estimated arrival {formatLocalDateTime(r.estimatedArrivalTime)}</p>
          )}

          {points.length > 0 && <MapView points={points} className="h-72" />}

          <section aria-labelledby="driver-h" className="border-t border-line pt-8">
            <h2 id="driver-h" className="sr-only">
              Driver
            </h2>
            <div className="flex items-center gap-4">
              <Avatar name={r.driver.fullName} src={r.driver.profilePicture} size="lg" />
              <div className="min-w-0">
                <p className="text-base font-semibold">{r.driver.fullName}</p>
                <RatingInline value={r.driver.averageRating} count={r.driver.totalReviews} />
                {user && !isDriver && (
                  <Link to={`/users/${r.driver.id}`} className="mt-1 block text-[13px] text-accent hover:underline">
                    View profile and reviews
                  </Link>
                )}
              </div>
            </div>
          </section>

          <section aria-labelledby="vehicle-h" className="border-t border-line pt-8">
            <h2 id="vehicle-h" className="mb-3 text-[13px] font-medium text-muted">
              Vehicle
            </h2>
            <div className="flex items-center gap-3">
              <Car className="size-5 text-faint" aria-hidden />
              <span>
                {r.vehicle.brand} {r.vehicle.model} · {r.vehicle.color} · {VEHICLE_TYPE_LABEL[r.vehicle.vehicleType]}
              </span>
              {r.vehicle.isVerified && (
                <span className="inline-flex items-center gap-1 text-[13px] text-success">
                  <BadgeCheck className="size-4" aria-hidden /> Verified
                </span>
              )}
            </div>
          </section>

          {r.description && (
            <section aria-labelledby="notes-h" className="border-t border-line pt-8">
              <h2 id="notes-h" className="mb-3 text-[13px] font-medium text-muted">
                From the driver
              </h2>
              <p className="whitespace-pre-line leading-relaxed text-ink">{r.description}</p>
            </section>
          )}
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="rounded-2xl border border-line bg-bg-2 p-5">
            <div className="flex items-baseline justify-between">
              <span className="text-muted">Price per seat</span>
              <span className="text-2xl font-semibold">{formatMoney(r.pricePerSeat)}</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-muted">
              <span className="inline-flex items-center gap-2">
                <Users className="size-4" aria-hidden /> Seats left
              </span>
              <span className="text-ink">
                {r.availableSeats} of {r.totalSeats}
              </span>
            </div>
            {canBook ? (
              <>
                {seatsWanted > r.availableSeats && (
                  <p className="mt-4 text-[13px] text-warning">Only {plural(r.availableSeats, 'seat')} left for your {seatsWanted} passengers.</p>
                )}
                <Button className="mt-5" block size="lg" onClick={onBook}>
                  {user ? 'Request to book' : 'Log in to book'}
                </Button>
              </>
            ) : (
              !isDriver &&
              unavailableReason && (
                <p className="mt-5 flex gap-2 text-[13px] text-muted">
                  <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
                  {unavailableReason}
                </p>
              )
            )}
            {isDriver && (
              <Link to={`/my-rides/${r.id}`} className={buttonClass('secondary', 'md', 'mt-5 w-full')}>
                Manage ride
              </Link>
            )}
            {showReviewCta && (
              <Button className="mt-5" block variant="secondary" onClick={() => setReviewOpen(true)}>
                Rate your driver
              </Button>
            )}
          </div>
        </aside>
      </div>

      {bookingOpen && <BookingModal ride={r} open onClose={() => setBookingOpen(false)} initialSeats={seatsWanted} />}
      {reviewOpen && (
        <ReviewModal open onClose={() => setReviewOpen(false)} rideId={r.id} reviewee={r.driver} role="driver" />
      )}
    </div>
  );
}
