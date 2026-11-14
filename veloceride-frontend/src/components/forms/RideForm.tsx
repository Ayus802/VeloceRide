import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Lock, MapPin, X } from 'lucide-react';
import { Button, buttonClass } from '@/components/common/Button';
import { Input, Select, Textarea } from '@/components/common/Field';
import { Notice } from '@/components/common/Feedback';
import { Segmented } from '@/components/common/Navigation';
import { MapView, type MapPoint } from '@/components/maps/MapView';
import type { RideRequest, Vehicle } from '@/types/dto';
import { rideSchema, type RideValues } from '@/utils/validation';
import { applyFieldErrors, getErrorMessage } from '@/utils/errors';
import { todayInPlatformZone } from '@/utils/format';

const FIELDS = [
  'vehicleId',
  'source',
  'destination',
  'departureDate',
  'departureTime',
  'estimatedArrivalTime',
  'totalSeats',
  'pricePerSeat',
  'description',
] as const;

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <fieldset className="border-t border-line pt-6 first:border-0 first:pt-0">
      <legend className="mb-4 text-[13px] font-semibold uppercase tracking-wider text-faint">{title}</legend>
      <div className="space-y-4">{children}</div>
    </fieldset>
  );
}

export function toRideRequest(v: RideValues): RideRequest {
  return {
    vehicleId: v.vehicleId,
    source: v.source.trim(),
    destination: v.destination.trim(),
    sourceLatitude: v.sourceLatitude,
    sourceLongitude: v.sourceLongitude,
    destinationLatitude: v.destinationLatitude,
    destinationLongitude: v.destinationLongitude,
    departureDate: v.departureDate,
    departureTime: v.departureTime.slice(0, 5),
    estimatedArrivalTime: v.estimatedArrivalTime ? `${v.estimatedArrivalTime.slice(0, 16)}:00` : undefined,
    totalSeats: v.totalSeats,
    pricePerSeat: v.pricePerSeat,
    description: v.description.trim() || undefined,
  };
}

/**
 * Publish / edit ride form. When the ride already has active bookings the backend locks route,
 * schedule, price and vehicle (409), so those inputs are read-only here (`lockedTerms`).
 */
export function RideForm({
  vehicles,
  defaultValues,
  lockedTerms = false,
  minSeats = 1,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  vehicles: Vehicle[];
  defaultValues?: Partial<RideValues>;
  lockedTerms?: boolean;
  minSeats?: number;
  submitLabel: string;
  onSubmit: (body: RideRequest) => Promise<unknown>;
  onCancel?: () => void;
}) {
  const today = todayInPlatformZone();
  const firstUsable = vehicles.find((v) => v.isVerified);
  const [formError, setFormError] = useState<string | null>(null);
  const [pinTarget, setPinTarget] = useState<'source' | 'destination'>('source');
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RideValues>({
    resolver: zodResolver(rideSchema),
    defaultValues: {
      vehicleId: firstUsable?.id ?? '',
      source: '',
      destination: '',
      departureDate: today,
      departureTime: '',
      estimatedArrivalTime: '',
      totalSeats: Math.min(3, firstUsable?.seatingCapacity ?? 3),
      pricePerSeat: undefined as unknown as number,
      description: '',
      ...defaultValues,
    },
  });

  const vehicleId = watch('vehicleId');
  const selected = vehicles.find((v) => v.id === vehicleId);
  const [sLat, sLng, dLat, dLng] = watch(['sourceLatitude', 'sourceLongitude', 'destinationLatitude', 'destinationLongitude']);
  const [srcName, dstName] = watch(['source', 'destination']);

  const points: MapPoint[] = [];
  if (sLat != null && sLng != null) points.push({ lat: sLat, lng: sLng, label: srcName || 'Departure', kind: 'start' });
  if (dLat != null && dLng != null) points.push({ lat: dLat, lng: dLng, label: dstName || 'Destination', kind: 'end' });

  const pin = (lat: number, lng: number) => {
    const opts = { shouldDirty: true };
    const round = (n: number) => Math.round(n * 1e6) / 1e6;
    if (pinTarget === 'source') {
      setValue('sourceLatitude', round(lat), opts);
      setValue('sourceLongitude', round(lng), opts);
      setPinTarget('destination');
    } else {
      setValue('destinationLatitude', round(lat), opts);
      setValue('destinationLongitude', round(lng), opts);
    }
  };
  const clearPin = (which: 'source' | 'destination') => {
    setValue(`${which}Latitude`, undefined, { shouldDirty: true });
    setValue(`${which}Longitude`, undefined, { shouldDirty: true });
  };

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    if (selected && values.totalSeats > selected.seatingCapacity) {
      setError('totalSeats', { message: `This vehicle has ${selected.seatingCapacity} passenger seats` });
      return;
    }
    if (values.totalSeats < minSeats) {
      setError('totalSeats', { message: `${minSeats} seat(s) are already booked` });
      return;
    }
    try {
      await onSubmit(toRideRequest(values));
    } catch (err) {
      if (!applyFieldErrors(err, setError, FIELDS)) setFormError(getErrorMessage(err));
    }
  });

  const numberOpts = { setValueAs: (v: string) => (v === '' ? undefined : Number(v)) };

  if (vehicles.length === 0) {
    return (
      <Notice tone="warning">
        Add a vehicle before publishing a ride.{' '}
        <Link to="/vehicles/new" className="font-medium underline">
          Add a vehicle
        </Link>
      </Notice>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-8">
      {formError && <Notice tone="danger">{formError}</Notice>}
      {lockedTerms && (
        <Notice tone="warning">
          <span className="inline-flex items-start gap-2">
            <Lock className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            Passengers have already requested or booked seats, so route, schedule, price and vehicle can’t change. To change them,
            cancel the ride and publish a new one.
          </span>
        </Notice>
      )}

      <Group title="Route">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Leaving from" placeholder="e.g. Delhi" maxLength={200} readOnly={lockedTerms} error={errors.source?.message} {...register('source')} />
          <Input label="Going to" placeholder="e.g. Jaipur" maxLength={200} readOnly={lockedTerms} error={errors.destination?.message} {...register('destination')} />
        </div>
        <details className="group rounded-lg border border-line" open={points.length > 0 || undefined}>
          <summary className="flex cursor-pointer select-none items-center gap-2 px-4 py-3 text-[13px] text-muted hover:text-ink">
            <MapPin className="size-4" aria-hidden />
            Pin exact pickup and drop-off on the map
            <span className="text-faint">(optional)</span>
          </summary>
          <div className="space-y-3 border-t border-line p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <Segmented
                label="Point to place"
                value={pinTarget}
                onChange={setPinTarget}
                options={[
                  { value: 'source', label: 'Pickup' },
                  { value: 'destination', label: 'Drop-off' },
                ]}
              />
              <span className="text-[13px] text-faint">Click the map to place the {pinTarget === 'source' ? 'pickup' : 'drop-off'} point.</span>
            </div>
            <MapView points={points} onMapClick={pin} className="h-72" />
            <div className="flex flex-wrap gap-2 text-[13px]">
              {sLat != null && (
                <button type="button" onClick={() => clearPin('source')} className="inline-flex items-center gap-1 rounded-md bg-surface px-2 py-1 text-muted hover:text-ink">
                  Pickup {sLat.toFixed(4)}, {sLng?.toFixed(4)} <X className="size-3" aria-label="Remove pickup pin" />
                </button>
              )}
              {dLat != null && (
                <button type="button" onClick={() => clearPin('destination')} className="inline-flex items-center gap-1 rounded-md bg-surface px-2 py-1 text-muted hover:text-ink">
                  Drop-off {dLat.toFixed(4)}, {dLng?.toFixed(4)} <X className="size-3" aria-label="Remove drop-off pin" />
                </button>
              )}
            </div>
          </div>
        </details>
      </Group>

      <Group title="When">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input type="date" label="Date" min={today} readOnly={lockedTerms} error={errors.departureDate?.message} {...register('departureDate')} />
          <Input type="time" label="Departure time" readOnly={lockedTerms} error={errors.departureTime?.message} {...register('departureTime')} />
        </div>
      </Group>

      <Group title="Vehicle and seats">
        {lockedTerms ? (
          <>
            <input type="hidden" {...register('vehicleId')} />
            <Input
              label="Vehicle"
              readOnly
              value={selected ? `${selected.vehicleName} · ${selected.brand} ${selected.model}` : 'Current vehicle'}
            />
          </>
        ) : (
        <Select
          label="Vehicle"
          error={errors.vehicleId?.message}
          hint={
            vehicles.some((v) => !v.isVerified) ? (
              <>
                Vehicles must be verified by VeloceRide before they can be used.{' '}
                <Link to="/vehicles" className="text-accent hover:underline">
                  Manage vehicles
                </Link>
              </>
            ) : undefined
          }
          {...register('vehicleId')}
        >
          <option value="" disabled>
            Choose a vehicle
          </option>
          {vehicles.map((v) => (
            <option key={v.id} value={v.id} disabled={!v.isVerified}>
              {v.vehicleName} · {v.brand} {v.model} ({v.seatingCapacity} seats){v.isVerified ? '' : ' — not verified'}
            </option>
          ))}
        </Select>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            type="number"
            label="Seats offered"
            min={Math.max(1, minSeats)}
            max={selected?.seatingCapacity ?? 20}
            inputMode="numeric"
            hint={selected ? `Up to ${selected.seatingCapacity}${minSeats > 1 ? ` · ${minSeats} already booked` : ''}` : undefined}
            error={errors.totalSeats?.message}
            {...register('totalSeats', numberOpts)}
          />
          <Input
            type="number"
            label="Price per seat (₹)"
            min={0}
            step="0.01"
            inputMode="decimal"
            readOnly={lockedTerms}
            error={errors.pricePerSeat?.message}
            {...register('pricePerSeat', numberOpts)}
          />
        </div>
      </Group>

      <details className="rounded-lg border border-line" open={Boolean(defaultValues?.description || defaultValues?.estimatedArrivalTime) || undefined}>
        <summary className="cursor-pointer select-none px-4 py-3 text-[13px] text-muted hover:text-ink">
          Optional details — arrival time and notes for passengers
        </summary>
        <div className="space-y-4 border-t border-line p-4">
          <Input
            type="datetime-local"
            label="Estimated arrival"
            optional
            error={errors.estimatedArrivalTime?.message}
            {...register('estimatedArrivalTime')}
          />
          <Textarea
            label="Notes"
            optional
            maxLength={1000}
            placeholder="Luggage space, music, stops you’re happy to make…"
            error={errors.description?.message}
            {...register('description')}
          />
        </div>
      </details>

      <div className="flex flex-col-reverse gap-2 border-t border-line pt-6 sm:flex-row sm:justify-end">
        {onCancel ? (
          <Button variant="ghost" onClick={onCancel} disabled={isSubmitting}>
            Cancel
          </Button>
        ) : (
          <Link to="/my-rides" className={buttonClass('ghost')}>
            Cancel
          </Link>
        )}
        <Button type="submit" size="lg" loading={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
