import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { vehicleService } from '@/services/vehicleService';
import { rideService } from '@/services/rideService';
import { bookingService } from '@/services/bookingService';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState, ErrorState, PageLoader } from '@/components/common/Feedback';
import { RideForm } from '@/components/forms/RideForm';
import { MAX_PAGE_SIZE } from '@/constants/config';

export default function EditRidePage() {
  const { rideId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const ride = useQuery({ queryKey: qk.ride(rideId), queryFn: () => rideService.get(rideId) });
  const vehicles = useQuery({
    queryKey: [...qk.vehicles, 'all'],
    queryFn: () => vehicleService.mine({ size: MAX_PAGE_SIZE, sort: 'createdAt,desc' }),
  });
  // Backend locks committed terms while any PENDING/ACCEPTED booking exists.
  const active = useQuery({
    queryKey: qk.received({ rideId, active: true }),
    queryFn: async () => {
      const [pending, accepted] = await Promise.all([
        bookingService.received({ rideId, status: 'PENDING', size: 1 }),
        bookingService.received({ rideId, status: 'ACCEPTED', size: 1 }),
      ]);
      return pending.totalElements + accepted.totalElements;
    },
  });

  if (ride.isPending || vehicles.isPending || active.isPending) return <PageLoader />;
  const err = ride.error ?? vehicles.error ?? active.error;
  if (err) return <ErrorState error={err} onRetry={() => [ride, vehicles, active].forEach((q) => q.refetch())} />;
  const r = ride.data!;
  if (r.driver.id !== user?.id) return <Navigate to={`/rides/${rideId}`} replace />;
  if (r.rideStatus !== 'SCHEDULED') {
    return <EmptyState title="This ride can no longer be edited" description="Only scheduled rides can be changed." />;
  }

  return (
    <div className="max-w-2xl">
      <PageHeader title="Edit ride" back={{ to: `/my-rides/${rideId}`, label: 'Back to ride' }} />
      <RideForm
        vehicles={vehicles.data!.content}
        lockedTerms={(active.data ?? 0) > 0}
        minSeats={Math.max(1, r.bookedSeats)}
        submitLabel="Save changes"
        onCancel={() => navigate(`/my-rides/${rideId}`)}
        defaultValues={{
          vehicleId: r.vehicle.id,
          source: r.source,
          destination: r.destination,
          sourceLatitude: r.sourceLatitude,
          sourceLongitude: r.sourceLongitude,
          destinationLatitude: r.destinationLatitude,
          destinationLongitude: r.destinationLongitude,
          departureDate: r.departureDate,
          departureTime: r.departureTime.slice(0, 5),
          estimatedArrivalTime: r.estimatedArrivalTime?.slice(0, 16) ?? '',
          totalSeats: r.totalSeats,
          pricePerSeat: Number(r.pricePerSeat),
          description: r.description ?? '',
        }}
        onSubmit={async (body) => {
          const updated = await rideService.update(rideId, body);
          queryClient.setQueryData(qk.ride(rideId), updated);
          void queryClient.invalidateQueries({ queryKey: qk.rides });
          toast.success('Ride updated');
          navigate(`/my-rides/${rideId}`, { replace: true });
        }}
      />
    </div>
  );
}
