import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Car, FileText, Pencil, Plus, Trash2 } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { vehicleService } from '@/services/vehicleService';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState, ErrorState, ListSkeleton, Notice } from '@/components/common/Feedback';
import { VerificationBadge } from '@/components/common/StatusBadge';
import { buttonClass } from '@/components/common/Button';
import { ActionsMenu, DropdownItem } from '@/components/dropdowns/Dropdown';
import { ConfirmDialog } from '@/components/modals/ConfirmDialog';
import { VEHICLE_TYPE_LABEL } from '@/constants/labels';
import { MAX_PAGE_SIZE } from '@/constants/config';
import type { Vehicle } from '@/types/dto';
import { getErrorMessage } from '@/utils/errors';

export default function MyVehiclesPage() {
  const queryClient = useQueryClient();
  const [toDelete, setToDelete] = useState<Vehicle | null>(null);
  const query = useQuery({
    queryKey: [...qk.vehicles, 'all'],
    queryFn: () => vehicleService.mine({ size: MAX_PAGE_SIZE, sort: 'createdAt,desc' }),
  });

  const remove = useMutation({
    mutationFn: (id: string) => vehicleService.remove(id),
    onSuccess: () => {
      toast.success('Vehicle removed');
      void queryClient.invalidateQueries({ queryKey: qk.vehicles });
      setToDelete(null);
    },
    onError: (err) => {
      toast.error('Couldn’t remove vehicle', getErrorMessage(err));
      setToDelete(null);
    },
  });

  const vehicles = query.data?.content ?? [];
  const hasPending = vehicles.some((v) => v.verificationStatus === 'PENDING');

  return (
    <>
      <PageHeader
        title="Vehicles"
        description="Cars you drive when offering rides."
        actions={
          <Link to="/vehicles/new" className={buttonClass('primary', 'sm')}>
            <Plus className="size-4" aria-hidden /> Add vehicle
          </Link>
        }
      />
      {hasPending && (
        <Notice className="mb-6 max-w-3xl">
          New and edited vehicles are checked by the VeloceRide team before they can be used to publish rides.
        </Notice>
      )}
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : vehicles.length === 0 ? (
        <EmptyState
          icon={<Car className="size-8" aria-hidden />}
          title="No vehicles yet"
          description="Add the car you drive to start publishing rides."
          action={
            <Link to="/vehicles/new" className={buttonClass('primary', 'sm')}>
              Add vehicle
            </Link>
          }
        />
      ) : (
        <ul className="max-w-3xl divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
          {vehicles.map((v) => (
            <li key={v.id} className="flex items-start gap-4 px-4 py-4 sm:px-5">
              <span className="mt-0.5 inline-flex size-10 shrink-0 items-center justify-center rounded-lg bg-surface text-muted">
                <Car className="size-5" aria-hidden />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium">{v.vehicleName}</p>
                  <VerificationBadge status={v.verificationStatus} />
                </div>
                <p className="mt-0.5 text-[13px] text-muted">
                  {v.brand} {v.model} · {v.color} · {VEHICLE_TYPE_LABEL[v.vehicleType]} · {v.seatingCapacity} seats
                </p>
                <p className="mt-0.5 font-mono text-[13px] tracking-wide text-faint">{v.vehicleNumber}</p>
                {v.verificationStatus === 'REJECTED' && v.rejectionReason && (
                  <p className="mt-2 text-[13px] text-danger">Rejected: {v.rejectionReason}. Update the details to request a new review.</p>
                )}
              </div>
              <ActionsMenu label={`Actions for ${v.vehicleName}`}>
                <DropdownItem to={`/vehicles/${v.id}/edit`} icon={<Pencil />}>
                  Edit
                </DropdownItem>
                {v.registrationDocumentUrl && (
                  <DropdownItem onSelect={() => window.open(v.registrationDocumentUrl, '_blank', 'noopener,noreferrer')} icon={<FileText />}>
                    View document
                  </DropdownItem>
                )}
                <DropdownItem tone="danger" icon={<Trash2 />} onSelect={() => setToDelete(v)}>
                  Remove
                </DropdownItem>
              </ActionsMenu>
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={Boolean(toDelete)}
        title={`Remove ${toDelete?.vehicleName ?? 'vehicle'}?`}
        description="You can’t remove a vehicle that has scheduled or ongoing rides."
        confirmLabel="Remove"
        tone="danger"
        loading={remove.isPending}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </>
  );
}
