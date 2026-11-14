import { BOOKING_STATUS_LABEL, PAYMENT_STATUS_LABEL, RIDE_STATUS_LABEL, VERIFICATION_LABEL } from '@/constants/labels';
import type { BookingStatus, PaymentStatus, RideStatus, VerificationStatus } from '@/types/dto';
import { cn } from '@/utils/cn';

type Tone = 'accent' | 'success' | 'warning' | 'danger' | 'neutral';

const toneClass: Record<Tone, string> = {
  accent: 'text-accent bg-accent-soft',
  success: 'text-success bg-success/10',
  warning: 'text-warning bg-warning/10',
  danger: 'text-danger bg-danger-soft',
  neutral: 'text-muted bg-surface',
};

export function Badge({ tone = 'neutral', children, className }: { tone?: Tone; children: React.ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium', toneClass[tone], className)}>
      {children}
    </span>
  );
}

const rideTone: Record<RideStatus, Tone> = { SCHEDULED: 'neutral', ONGOING: 'accent', COMPLETED: 'success', CANCELLED: 'danger' };
const bookingTone: Record<BookingStatus, Tone> = {
  PENDING: 'warning',
  ACCEPTED: 'accent',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
  COMPLETED: 'success',
};
const paymentTone: Record<PaymentStatus, Tone> = { PENDING: 'warning', SUCCESS: 'success', FAILED: 'danger', REFUNDED: 'neutral' };
const verificationTone: Record<VerificationStatus, Tone> = { PENDING: 'warning', VERIFIED: 'success', REJECTED: 'danger' };

export const RideStatusBadge = ({ status }: { status: RideStatus }) => (
  <Badge tone={rideTone[status]}>
    {status === 'ONGOING' && <span className="size-1.5 animate-pulse rounded-full bg-accent" aria-hidden />}
    {RIDE_STATUS_LABEL[status]}
  </Badge>
);
export const BookingStatusBadge = ({ status }: { status: BookingStatus }) => (
  <Badge tone={bookingTone[status]}>{BOOKING_STATUS_LABEL[status]}</Badge>
);
export const PaymentStatusBadge = ({ status }: { status: PaymentStatus }) => (
  <Badge tone={paymentTone[status]}>{PAYMENT_STATUS_LABEL[status]}</Badge>
);
export const VerificationBadge = ({ status }: { status: VerificationStatus }) => (
  <Badge tone={verificationTone[status]}>{VERIFICATION_LABEL[status]}</Badge>
);
