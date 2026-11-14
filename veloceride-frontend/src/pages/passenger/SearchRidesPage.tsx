import { useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowDownUp, Check } from 'lucide-react';
import { qk } from '@/api/queryClient';
import { rideService } from '@/services/rideService';
import { RideSearchForm } from '@/components/forms/RideSearchForm';
import { searchUrl } from '@/constants/routes';
import { RideRow } from '@/components/rides/RideBits';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/Feedback';
import { Pagination } from '@/components/common/Navigation';
import { Dropdown, DropdownItem } from '@/components/dropdowns/Dropdown';
import { formatDate, plural, todayInPlatformZone } from '@/utils/format';

const SORTS = [
  { value: 'departureTime,asc', label: 'Earliest departure' },
  { value: 'pricePerSeat,asc', label: 'Lowest price' },
  { value: 'availableSeats,desc', label: 'Most seats' },
] as const;

/** Pages 11 + 12: search form on top, results below. All filtering is done by the backend. */
export default function SearchRidesPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const date = params.get('date') ?? '';
  const seats = Math.max(1, Number(params.get('seats')) || 1);
  const sort = SORTS.find((s) => s.value === params.get('sort'))?.value ?? SORTS[0].value;
  const page = Math.max(0, Number(params.get('page')) || 0);
  const ready = Boolean(from && to && date && date >= todayInPlatformZone());

  const query = useQuery({
    queryKey: qk.rideSearch({ from, to, date, sort, page }),
    queryFn: () => rideService.search({ source: from, destination: to, date, sort, page, size: 20 }),
    enabled: ready,
    placeholderData: keepPreviousData,
  });

  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => next.set(k, v));
    setParams(next);
  };

  const data = query.data;
  const sortLabel = SORTS.find((s) => s.value === sort)!.label;

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 sm:py-10">
      <h1 className="sr-only">Find a ride</h1>
      <div className="rounded-2xl border border-line bg-bg-2 p-3 sm:p-4">
        <RideSearchForm
          key={`${from}|${to}|${date}|${seats}`}
          initial={{ from, to, date, seats }}
          onSubmit={(v) => navigate(searchUrl(v))}
        />
      </div>

      {!ready ? (
        <EmptyState
          className="mt-6"
          title="Where are you heading?"
          description="Enter a departure, destination and date to see available rides."
        />
      ) : (
        <div className="mt-8">
          <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold">
                {from} → {to}
              </h2>
              <p className="text-[13px] text-muted">
                {formatDate(date)} · {plural(seats, 'passenger')}
                {data && ` · ${plural(data.totalElements, 'ride')} available`}
              </p>
            </div>
            <Dropdown
              trigger={(props) => (
                <button
                  type="button"
                  className="inline-flex h-9 items-center gap-2 rounded-lg border border-line px-3 text-[13px] text-muted hover:text-ink"
                  {...props}
                >
                  <ArrowDownUp className="size-3.5" aria-hidden />
                  {sortLabel}
                </button>
              )}
            >
              {SORTS.map((s) => (
                <DropdownItem key={s.value} onSelect={() => update({ sort: s.value, page: '0' })} icon={s.value === sort ? <Check /> : <span className="size-4" />}>
                  {s.label}
                </DropdownItem>
              ))}
            </Dropdown>
          </div>

          {query.isPending ? (
            <ListSkeleton rows={4} />
          ) : query.isError ? (
            <ErrorState error={query.error} onRetry={() => query.refetch()} />
          ) : data!.content.length === 0 ? (
            <EmptyState
              title="No rides found"
              description="Try another date, or a broader place name — matching is by partial name, e.g. “Delhi” matches “New Delhi”."
            />
          ) : (
            <>
              <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card" aria-busy={query.isFetching}>
                {data!.content.map((ride) => (
                  <RideRow key={ride.id} ride={ride} seatsWanted={seats} to={`/rides/${ride.id}?seats=${seats}`} />
                ))}
              </ul>
              <Pagination page={data!.page} totalPages={data!.totalPages} onChange={(p) => update({ page: String(p) })} />
            </>
          )}
        </div>
      )}
    </div>
  );
}
