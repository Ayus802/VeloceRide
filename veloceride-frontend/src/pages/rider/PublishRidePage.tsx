import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { vehicleService } from '@/services/vehicleService';
import { rideService } from '@/services/rideService';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { ErrorState, PageLoader } from '@/components/common/Feedback';
import { RideForm } from '@/components/forms/RideForm';
import { MAX_PAGE_SIZE } from '@/constants/config';

export default function PublishRidePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const vehicles = useQuery({
    queryKey: [...qk.vehicles, 'all'],
    queryFn: () => vehicleService.mine({ size: MAX_PAGE_SIZE, sort: 'createdAt,desc' }),
  });

  return (
    <div className="max-w-2xl">
      <PageHeader title="Publish a ride" description="Offer the empty seats on a trip you’re already making." />
      {vehicles.isPending ? (
        <PageLoader />
      ) : vehicles.isError ? (
        <ErrorState error={vehicles.error} onRetry={() => vehicles.refetch()} />
      ) : (
        <RideForm
          vehicles={vehicles.data.content}
          submitLabel="Publish ride"
          onSubmit={async (body) => {
            const ride = await rideService.publish(body);
            void queryClient.invalidateQueries({ queryKey: qk.rides });
            toast.success('Ride published', 'Passengers can now find and request it.');
            navigate(`/my-rides/${ride.id}`, { replace: true });
          }}
        />
      )}
    </div>
  );
}
