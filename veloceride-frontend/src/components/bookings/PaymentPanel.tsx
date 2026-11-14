import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CreditCard } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { paymentService } from '@/services/paymentService';
import { Button } from '@/components/common/Button';
import { Notice, Spinner } from '@/components/common/Feedback';
import { PaymentStatusBadge } from '@/components/common/StatusBadge';
import { Segmented } from '@/components/common/Navigation';
import { PAYMENT_METHOD_LABEL } from '@/constants/labels';
import { MAX_PAGE_SIZE } from '@/constants/config';
import { toast } from '@/store/toastStore';
import type { Booking, PaymentMethod } from '@/types/dto';
import { formatInstant, formatMoney } from '@/utils/format';
import { getErrorMessage } from '@/utils/errors';

const ONLINE_METHODS: PaymentMethod[] = ['UPI', 'CARD', 'ONLINE'];

/**
 * Passenger payment for an ACCEPTED/COMPLETED booking via the backend's payment provider
 * (currently the SIMULATED sandbox — no real money moves). The server decides the outcome.
 * BookingResponse carries no payment status (G-06), so the latest payment is found in my-payments.
 * CASH is not offered: the driver has no way to look up the payment id to confirm it (G-07).
 */
export function PaymentPanel({ booking }: { booking: Booking }) {
  const queryClient = useQueryClient();
  const [method, setMethod] = useState<PaymentMethod>('UPI');
  const payable = booking.bookingStatus === 'ACCEPTED' || booking.bookingStatus === 'COMPLETED';

  const paymentsQuery = useQuery({
    queryKey: [...qk.payments, 'mine', MAX_PAGE_SIZE],
    queryFn: () => paymentService.mine({ size: MAX_PAGE_SIZE, sort: 'createdAt,desc' }),
  });
  const latest = paymentsQuery.data?.content.find((p) => p.bookingId === booking.id);

  const refresh = () => queryClient.invalidateQueries({ queryKey: qk.payments });

  const initiate = useMutation({
    mutationFn: () => paymentService.initiate(booking.id, method),
    onSuccess: refresh,
  });
  const confirm = useMutation({
    mutationFn: (paymentId: string) => paymentService.confirm(paymentId),
    onSuccess: (p) => {
      void refresh();
      if (p.paymentStatus === 'SUCCESS') toast.success('Payment successful', `${formatMoney(p.amount)} paid.`);
      else if (p.paymentStatus === 'FAILED') toast.error('Payment failed', p.failureReason);
      else toast.info('Payment is still processing');
    },
  });

  if (!payable && !latest) return null;

  const error = initiate.error ?? confirm.error;
  const busy = initiate.isPending || confirm.isPending;
  const canStartNew = payable && (!latest || latest.paymentStatus === 'FAILED' || latest.paymentStatus === 'PENDING');

  return (
    <section aria-labelledby="payment-h" className="mt-10">
      <h2 id="payment-h" className="mb-3 text-base font-semibold">
        Payment
      </h2>
      <div className="rounded-xl border border-line bg-bg-2 p-5">
        {paymentsQuery.isPending ? (
          <Spinner />
        ) : (
          <div className="space-y-5">
            {latest && (
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-medium">
                    {formatMoney(latest.amount)} · {PAYMENT_METHOD_LABEL[latest.paymentMethod]}
                  </p>
                  <p className="mt-0.5 text-[13px] text-muted">
                    {latest.transactionReference} · {formatInstant(latest.updatedAt)}
                  </p>
                  {latest.paymentStatus === 'FAILED' && latest.failureReason && (
                    <p className="mt-1 text-[13px] text-danger">{latest.failureReason}</p>
                  )}
                </div>
                <PaymentStatusBadge status={latest.paymentStatus} />
              </div>
            )}

            {latest?.paymentStatus === 'PENDING' && latest.paymentMethod !== 'CASH' && (
              <div className="flex flex-wrap items-center gap-3">
                <Button icon={<CreditCard className="size-4" />} loading={confirm.isPending} disabled={busy} onClick={() => confirm.mutate(latest.id)}>
                  Complete payment
                </Button>
                {latest.provider === 'SIMULATED' && <span className="text-[13px] text-faint">Test mode — no real money is charged.</span>}
              </div>
            )}

            {latest?.paymentStatus === 'PENDING' && latest.paymentMethod === 'CASH' && (
              <Notice tone="warning">
                Cash payment of {formatMoney(latest.amount)} is awaiting the driver’s confirmation. You can pay online instead below.
              </Notice>
            )}

            {canStartNew && latest?.paymentStatus !== 'PENDING' && (
              <div className="space-y-4">
                {!latest && <p className="text-muted">Pay {formatMoney(booking.totalAmount)} for this booking.</p>}
                <div className="flex flex-wrap items-center gap-3">
                  <Segmented
                    label="Payment method"
                    value={method}
                    onChange={setMethod}
                    options={ONLINE_METHODS.map((m) => ({ value: m, label: PAYMENT_METHOD_LABEL[m] }))}
                  />
                  <Button loading={initiate.isPending} disabled={busy} onClick={() => initiate.mutate()}>
                    {latest?.paymentStatus === 'FAILED' ? 'Try again' : 'Pay now'}
                  </Button>
                </div>
                <p className="text-[13px] text-faint">Paying in cash? Hand it to the driver on the day.</p>
              </div>
            )}

            {canStartNew && latest?.paymentStatus === 'PENDING' && (
              <details className="text-[13px] text-muted">
                <summary className="cursor-pointer select-none hover:text-ink">Use a different method</summary>
                <div className="mt-3 flex flex-wrap items-center gap-3">
                  <Segmented
                    label="Payment method"
                    value={method}
                    onChange={setMethod}
                    options={ONLINE_METHODS.map((m) => ({ value: m, label: PAYMENT_METHOD_LABEL[m] }))}
                  />
                  <Button size="sm" variant="secondary" loading={initiate.isPending} disabled={busy} onClick={() => initiate.mutate()}>
                    Start new payment
                  </Button>
                </div>
              </details>
            )}

            {latest?.paymentStatus === 'REFUNDED' && <p className="text-[13px] text-muted">This payment was refunded after the booking was cancelled.</p>}
            {error && <Notice tone="danger">{getErrorMessage(error)}</Notice>}
          </div>
        )}
      </div>
    </section>
  );
}
