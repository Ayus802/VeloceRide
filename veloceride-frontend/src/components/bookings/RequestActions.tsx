import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, X } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { bookingService } from '@/services/bookingService';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/modals/ConfirmDialog';
import { toast } from '@/store/toastStore';
import type { Booking } from '@/types/dto';
import { formatMoney, plural } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

/** Accept / Decline for a PENDING request, each behind a confirmation. Backend validates seats and ownership. */
export function RequestActions({ booking }: { booking: Booking }) {
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<'accept' | 'reject' | null>(null);
  const mutation = useMutation({
    mutationFn: (kind: 'accept' | 'reject') => (kind === 'accept' ? bookingService.accept(booking.id) : bookingService.reject(booking.id)),
    onSuccess: (_, kind) => {
      toast.success(kind === 'accept' ? `${booking.passenger.fullName} is confirmed` : 'Request declined');
      void queryClient.invalidateQueries({ queryKey: qk.bookings });
      void queryClient.invalidateQueries({ queryKey: qk.rides });
      setPending(null);
    },
    onError: (err) => {
      toast.error('Couldn’t update the request', getErrorMessage(err));
      void queryClient.invalidateQueries({ queryKey: qk.bookings });
      setPending(null);
    },
  });
  return (
    <>
      <Button size="sm" variant="secondary" icon={<X className="size-4" />} onClick={() => setPending('reject')}>
        Decline
      </Button>
      <Button size="sm" icon={<Check className="size-4" />} onClick={() => setPending('accept')}>
        Accept
      </Button>
      <ConfirmDialog
        open={pending === 'accept'}
        title={`Accept ${booking.passenger.fullName}?`}
        description={`${plural(booking.numberOfSeats, 'seat')} will be reserved for ${formatMoney(booking.totalAmount)}. You’ll both see each other’s phone number.`}
        confirmLabel="Accept"
        cancelLabel="Back"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate('accept')}
        onClose={() => setPending(null)}
      />
      <ConfirmDialog
        open={pending === 'reject'}
        title={`Decline ${booking.passenger.fullName}?`}
        description="They’ll be notified that you declined."
        confirmLabel="Decline"
        cancelLabel="Back"
        tone="danger"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate('reject')}
        onClose={() => setPending(null)}
      />
    </>
  );
}
