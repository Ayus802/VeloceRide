import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, BadgeCheck, Coins, Navigation, Repeat, Search, Smile, UserCheck } from 'lucide-react';
import { RideSearchForm, type RideSearchValues } from '@/components/forms/RideSearchForm';
import { RideRow } from '@/components/rides/RideBits';
import { PageLoader } from '@/components/common/Feedback';
import { buttonClass } from '@/components/common/Button';
import { searchUrl } from '@/constants/routes';
import { rideService } from '@/services/rideService';
import { useAuthStore } from '@/store/authStore';

const steps = [
  { icon: Search, title: 'Find a ride', text: 'Enter where you’re going and when. Compare drivers, departure times, seats and price per seat.' },
  { icon: UserCheck, title: 'Connect with a rider', text: 'Request your seats. Once the driver accepts, you can see each other’s phone number to coordinate.' },
  { icon: Smile, title: 'Enjoy the journey', text: 'Follow the car on the map when it sets off, then rate each other after you arrive.' },
];

/** Benefits that map to real features of the backend — no unverifiable claims. */
const benefits = [
  { icon: Coins, title: 'Share travel costs', text: 'Drivers set a price per seat. Passengers pay only for the seats they book.' },
  { icon: BadgeCheck, title: 'Confirmed by the driver', text: 'Seats are reserved only when the driver accepts your request, so plans are clear on both sides.' },
  { icon: Navigation, title: 'Follow the ride live', text: 'Passengers with a confirmed booking can see the driver’s location once the ride starts.' },
  { icon: Repeat, title: 'One account, both ways', text: 'Book a seat today and publish your own ride tomorrow, with the same account.' },
];

/** Real upcoming rides from the public GET /api/rides endpoint; the section hides itself if there are none. */
function LeavingSoon() {
  const query = useQuery({
    queryKey: ['rides', 'upcoming', { size: 4 }],
    queryFn: () => rideService.upcoming({ size: 4 }),
    retry: false,
  });
  const rides = query.data?.content ?? [];
  if (!rides.length) return null;
  return (
    <section aria-labelledby="leaving-h" className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h2 id="leaving-h" className="text-xl font-semibold tracking-tight">
            Rides leaving soon
          </h2>
          <p className="mt-1 text-muted">Published by drivers on VeloceRide, with seats still available.</p>
        </div>
        <Link to="/rides/search" className="hidden shrink-0 items-center gap-1 text-[13px] font-medium text-accent hover:underline sm:inline-flex">
          Search all rides <ArrowRight className="size-3.5" aria-hidden />
        </Link>
      </div>
      <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-bg-2 shadow-card">
        {rides.map((ride) => (
          <RideRow key={ride.id} ride={ride} to={`/rides/${ride.id}`} showDate />
        ))}
      </ul>
    </section>
  );
}

export default function HomePage() {
  const status = useAuthStore((s) => s.status);
  const navigate = useNavigate();

  // Signed-in users land on their dashboard; visitors get the landing page.
  if (status === 'authenticated') return <Navigate to="/dashboard" replace />;
  if (status === 'booting') return <PageLoader />;

  const onSearch = (v: RideSearchValues) => navigate(searchUrl(v));

  return (
    <>
      {/* Hero */}
      <section className="border-b border-line/70">
        <div className="mx-auto max-w-6xl px-4 pb-16 pt-16 sm:px-6 sm:pb-20 sm:pt-24">
          <div className="mx-auto max-w-2xl text-center">
            <h1 className="text-4xl font-semibold tracking-tight text-ink sm:text-5xl">Your journey, shared.</h1>
            <p className="mx-auto mt-4 max-w-xl text-base text-muted">
              Find affordable rides, meet fellow travellers, and make every journey easier with VeloceRide.
            </p>
            <div className="mt-7 flex flex-col justify-center gap-2 sm:flex-row">
              <Link to="/rides/search" className={buttonClass('primary', 'lg')}>
                <Search className="size-4" aria-hidden /> Find a ride
              </Link>
              <Link to="/rides/new" className={buttonClass('secondary', 'lg')}>
                Publish a ride
              </Link>
            </div>
          </div>

          <div className="mx-auto mt-12 max-w-5xl rounded-2xl border border-line bg-bg-2 p-3 shadow-pop sm:p-4">
            <h2 className="sr-only">Search rides</h2>
            <RideSearchForm onSubmit={onSearch} submitLabel="Search rides" />
          </div>
          <p className="mt-3 text-center text-[13px] text-faint">No account needed to search.</p>
        </div>
      </section>

      <LeavingSoon />

      {/* How it works */}
      <section id="how-it-works" aria-labelledby="how-h" className="border-t border-line/70 bg-bg-2/60">
        <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 sm:py-20">
          <h2 id="how-h" className="text-xl font-semibold tracking-tight">
            How VeloceRide works
          </h2>
          <ol className="mt-10 grid gap-10 sm:grid-cols-3 sm:gap-8">
            {steps.map(({ icon: Icon, title, text }, i) => (
              <li key={title} className="relative">
                <div className="flex items-center gap-3">
                  <span className="inline-flex size-10 items-center justify-center rounded-xl bg-accent-soft text-accent">
                    <Icon className="size-5" aria-hidden />
                  </span>
                  <span className="text-[13px] font-medium text-faint">Step {i + 1}</span>
                </div>
                <h3 className="mt-4 text-base font-semibold">{title}</h3>
                <p className="mt-1.5 leading-relaxed text-muted">{text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Why VeloceRide */}
      <section aria-labelledby="why-h" className="border-t border-line/70">
        <div className="mx-auto grid max-w-6xl gap-10 px-4 py-16 sm:px-6 sm:py-20 lg:grid-cols-[18rem_1fr]">
          <div>
            <h2 id="why-h" className="text-xl font-semibold tracking-tight">
              Why VeloceRide
            </h2>
            <p className="mt-2 text-muted">A simpler way to fill empty seats and travel for less.</p>
          </div>
          <dl className="grid gap-x-10 gap-y-8 sm:grid-cols-2">
            {benefits.map(({ icon: Icon, title, text }) => (
              <div key={title} className="flex gap-4">
                <Icon className="mt-0.5 size-5 shrink-0 text-accent" aria-hidden />
                <div>
                  <dt className="font-semibold">{title}</dt>
                  <dd className="mt-1 leading-relaxed text-muted">{text}</dd>
                </div>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* Closing call to action */}
      <section aria-labelledby="cta-h" className="border-t border-line/70">
        <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 sm:py-20">
          <div className="flex flex-col items-start justify-between gap-6 rounded-2xl border border-line bg-bg-2 px-6 py-10 shadow-card sm:flex-row sm:items-center sm:px-10">
            <div>
              <h2 id="cta-h" className="text-xl font-semibold tracking-tight">
                Ready for your next journey?
              </h2>
              <p className="mt-1 text-muted">Search for free, or join to book seats and publish your own rides.</p>
            </div>
            <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
              <Link to="/rides/search" className={buttonClass('secondary', 'lg')}>
                Find a ride
              </Link>
              <Link to="/register" className={buttonClass('primary', 'lg')}>
                Join VeloceRide
              </Link>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
