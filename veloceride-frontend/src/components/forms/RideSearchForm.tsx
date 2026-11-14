import { useState, type FormEvent } from 'react';
import { ArrowUpDown, Search } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { todayInPlatformZone } from '@/utils/format';
import { cn } from '@/utils/cn';

export interface RideSearchValues {
  from: string;
  to: string;
  date: string;
  seats: number;
}

const fieldClass =
  'h-11 w-full rounded-lg border border-line bg-bg-2 px-3 text-sm text-ink placeholder:text-faint hover:border-line-strong ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/25';

/**
 * From / To / Date / Seats. Date is required because GET /api/rides/search requires it.
 * Seats is not a backend search filter (see BACKEND_API_GAPS G-02); it is carried to results/booking.
 */
export function RideSearchForm({
  initial,
  onSubmit,
  layout = 'bar',
  submitLabel = 'Search',
}: {
  initial?: Partial<RideSearchValues>;
  onSubmit: (values: RideSearchValues) => void;
  layout?: 'bar' | 'stack';
  submitLabel?: string;
}) {
  const today = todayInPlatformZone();
  const [values, setValues] = useState<RideSearchValues>({
    from: initial?.from ?? '',
    to: initial?.to ?? '',
    date: initial?.date && initial.date >= today ? initial.date : today,
    seats: initial?.seats ?? 1,
  });
  const [error, setError] = useState<string | null>(null);

  const set = <K extends keyof RideSearchValues>(k: K, v: RideSearchValues[K]) => setValues((s) => ({ ...s, [k]: v }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const from = values.from.trim();
    const to = values.to.trim();
    if (!from || !to) return setError('Enter where you’re leaving from and going to.');
    if (from.length > 200 || to.length > 200) return setError('Locations must be under 200 characters.');
    if (!values.date || values.date < today) return setError('Choose today or a later date.');
    setError(null);
    onSubmit({ ...values, from, to });
  };

  return (
    <form onSubmit={submit} noValidate aria-label="Search rides">
      <div
        className={cn(
          'grid gap-2',
          layout === 'bar' ? 'md:grid-cols-[1fr_auto_1fr_10rem_7rem_auto] md:items-center' : 'sm:grid-cols-2',
        )}
      >
        <label className={cn(layout === 'stack' && 'sm:col-span-2')}>
          <span className="sr-only">Leaving from</span>
          <input className={fieldClass} placeholder="Leaving from" value={values.from} onChange={(e) => set('from', e.target.value)} maxLength={200} autoComplete="off" />
        </label>
        <button
          type="button"
          onClick={() => setValues((s) => ({ ...s, from: s.to, to: s.from }))}
          className={cn(
            'inline-flex size-9 items-center justify-center justify-self-center rounded-full border border-line text-muted hover:text-ink',
            layout === 'stack' && 'sm:col-span-2',
          )}
          aria-label="Swap departure and destination"
          title="Swap"
        >
          <ArrowUpDown className="size-4 md:rotate-90" aria-hidden />
        </button>
        <label className={cn(layout === 'stack' && 'sm:col-span-2')}>
          <span className="sr-only">Going to</span>
          <input className={fieldClass} placeholder="Going to" value={values.to} onChange={(e) => set('to', e.target.value)} maxLength={200} autoComplete="off" />
        </label>
        <label>
          <span className="sr-only">Date</span>
          <input type="date" className={fieldClass} min={today} value={values.date} onChange={(e) => set('date', e.target.value)} />
        </label>
        <label>
          <span className="sr-only">Seats</span>
          <select className={cn(fieldClass, 'appearance-none')} value={values.seats} onChange={(e) => set('seats', Number(e.target.value))}>
            {Array.from({ length: 8 }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>
                {n} {n === 1 ? 'seat' : 'seats'}
              </option>
            ))}
          </select>
        </label>
        <Button type="submit" size="lg" icon={<Search className="size-4" />} className={cn(layout === 'stack' && 'sm:col-span-2')}>
          {submitLabel}
        </Button>
      </div>
      {error && (
        <p role="alert" className="mt-2 text-[13px] text-danger">
          {error}
        </p>
      )}
    </form>
  );
}
