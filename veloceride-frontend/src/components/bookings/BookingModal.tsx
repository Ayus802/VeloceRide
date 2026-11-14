import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Minus, Plus } from 'lucide-react';
import { Modal } from '@/components/modals/Modal';
import { Button, IconButton } from '@/components/common/Button';
import { Notice } from '@/components/common/Feedback';
import { RouteTimeline } from '@/components/rides/RideBits';
import { bookingService } from '@/services/bookingService';
import { qk } from '@/api/queryClient';
import { toast } from '@/store/toastStore';
import type { Ride } from '@/types/dto';
import { formatDate, formatMoney } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

/**
 * Booking confirmation. The total shown is an estimate (seats × price); the backend computes the
 * authoritative amount and validates seat availability. The request starts PENDING.
 */
export function BookingModal({
  ride,
  open,
  onClose,
  initialSeats = 1,
}: {
  ride: Ride;
  open: boolean;
  onClose: () => void;
  initialSeats?: number;
}) {
  const max = Math.max(1, Math.min(ride.availableSeats, 20));
  const [seats, setSeats] = useState(Math.min(Math.max(1, initialSeats), max));
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => bookingService.create(ride.id, seats),
    onSuccess: (booking) => {
      void queryClient.invalidateQueries({ queryKey: qk.bookings });
      void queryClient.invalidateQueries({ queryKey: qk.ride(ride.id) });
      toast.success('Request sent', `${ride.driver.fullName} will confirm your seat${seats > 1 ? 's' : ''}.`);
      onClose();
      navigate(`/bookings/${booking.id}`);
    },
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={mutation.isPending}
      title="Request to book"
      description={`${formatDate(ride.departureDate)} with ${ride.driver.fullName}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button onClick={() => mutation.mutate()} loading={mutation.isPending} data-autofocus>
            Send request
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        <RouteTimeline source={ride.source} destination={ride.destination} departureTime={ride.departureTime} compact />

        <div className="flex items-center justify-between border-t border-line pt-4">
          <span id="seat-label" className="text-muted">
            Seats
          </span>
          <div className="flex items-center gap-3" role="group" aria-labelledby="seat-label">
            <IconButton label="Fewer seats" disabled={seats <= 1} onClick={() => setSeats((s) => s - 1)} className="border border-line">
              <Minus className="size-4" />
            </IconButton>
            <span className="w-6 text-center text-base font-semibold tabular-nums" aria-live="polite">
              {seats}
            </span>
            <IconButton label="More seats" disabled={seats >= max} onClick={() => setSeats((s) => s + 1)} className="border border-line">
              <Plus className="size-4" />
            </IconButton>
          </div>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-muted">
            Estimated total
            <span className="block text-xs text-faint">
              {seats} × {formatMoney(ride.pricePerSeat)} · final amount confirmed by VeloceRide
            </span>
          </span>
          <span className="text-lg font-semibold">{formatMoney(seats * ride.pricePerSeat)}</span>
        </div>

        <Notice>Your request stays pending until the driver accepts it. Seats are only reserved once accepted.</Notice>
        {mutation.isError && <Notice tone="danger">{getErrorMessage(mutation.error)}</Notice>}
      </div>
    </Modal>
  );
}
