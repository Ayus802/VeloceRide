import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, ExternalLink, X } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { adminService } from '@/services/adminService';
import { toast } from '@/store/toastStore';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination, Tabs } from '@/components/common/Navigation';
import { VerificationBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Field';
import { Modal } from '@/components/modals/Modal';
import { VEHICLE_TYPE_LABEL } from '@/constants/labels';
import type { Vehicle, VerificationStatus } from '@/types/dto';
import { formatInstant } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

/** Vehicle verification queue (required before vehicles can be used in prod). */
export default function AdminVehiclesPage() {
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const status = (['PENDING', 'VERIFIED', 'REJECTED'].includes(params.get('status') ?? '') ? params.get('status') : 'PENDING') as VerificationStatus;
  const page = Math.max(0, Number(params.get('page')) || 0);
  const [rejecting, setRejecting] = useState<Vehicle | null>(null);
  const [reason, setReason] = useState('');

  const query = useQuery({
    queryKey: [...qk.admin, 'vehicles', { status, page }],
    queryFn: () => adminService.vehicles({ status, page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  const done = (msg: string) => {
    toast.success(msg);
    void queryClient.invalidateQueries({ queryKey: qk.admin });
  };
  const verify = useMutation({
    mutationFn: (v: Vehicle) => adminService.verifyVehicle(v.id),
    onSuccess: (v) => done(`${v.vehicleName} verified`),
    onError: (err) => toast.error('Couldn’t verify', getErrorMessage(err)),
  });
  const reject = useMutation({
    mutationFn: () => adminService.rejectVehicle(rejecting!.id, reason.trim()),
    onSuccess: (v) => {
      done(`${v.vehicleName} rejected`);
      setRejecting(null);
      setReason('');
    },
  });

  return (
    <>
      <PageHeader title="Vehicles" description="Owners can publish rides only with verified vehicles." />
      <Tabs
        className="mb-4"
        value={status}
        onChange={(s) => setParams({ status: s })}
        items={[
          { value: 'PENDING', label: 'Pending' },
          { value: 'VERIFIED', label: 'Verified' },
          { value: 'REJECTED', label: 'Rejected' },
        ]}
      />
      {query.isPending ? (
        <ListSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState title={status === 'PENDING' ? 'Nothing to review' : 'No vehicles'} />
      ) : (
        <>
          <ul className="divide-y divide-line">
            {query.data.content.map((v) => (
              <li key={v.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{v.vehicleName}</span>
                    <span className="font-mono text-[13px] tracking-wide text-muted">{v.vehicleNumber}</span>
                    <VerificationBadge status={v.verificationStatus} />
                  </div>
                  <p className="mt-0.5 text-[13px] text-muted">
                    {v.brand} {v.model} · {v.color} · {VEHICLE_TYPE_LABEL[v.vehicleType]} · {v.seatingCapacity} seats · added {formatInstant(v.createdAt, true)}
                  </p>
                  {v.rejectionReason && <p className="mt-1 text-[13px] text-danger">Reason: {v.rejectionReason}</p>}
                  {v.registrationDocumentUrl ? (
                    <a href={v.registrationDocumentUrl} target="_blank" rel="noopener noreferrer" className="mt-1 inline-flex items-center gap-1 text-[13px] text-accent hover:underline">
                      Registration document <ExternalLink className="size-3" aria-hidden />
                    </a>
                  ) : (
                    <p className="mt-1 text-[13px] text-faint">No document provided</p>
                  )}
                </div>
                <div className="flex gap-2">
                  {v.verificationStatus !== 'REJECTED' && (
                    <Button size="sm" variant="secondary" icon={<X className="size-4" />} onClick={() => setRejecting(v)}>
                      Reject
                    </Button>
                  )}
                  {v.verificationStatus !== 'VERIFIED' && (
                    <Button size="sm" icon={<Check className="size-4" />} loading={verify.isPending && verify.variables?.id === v.id} onClick={() => verify.mutate(v)}>
                      Verify
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={query.data.page} totalPages={query.data.totalPages} onChange={(p) => setParams({ status, page: String(p) })} />
        </>
      )}

      <Modal
        open={Boolean(rejecting)}
        onClose={() => setRejecting(null)}
        busy={reject.isPending}
        title={`Reject ${rejecting?.vehicleName ?? ''}?`}
        description="The owner sees this reason and can update the vehicle to request a new review."
        footer={
          <>
            <Button variant="ghost" onClick={() => setRejecting(null)} disabled={reject.isPending}>
              Cancel
            </Button>
            <Button variant="danger" loading={reject.isPending} disabled={!reason.trim()} onClick={() => reject.mutate()}>
              Reject vehicle
            </Button>
          </>
        }
      >
        <Textarea
          label="Reason"
          maxLength={500}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="e.g. Registration document is unreadable"
          error={reject.isError ? getErrorMessage(reject.error) : undefined}
          data-autofocus
        />
      </Modal>
    </>
  );
}
