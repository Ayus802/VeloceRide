import { Link } from 'react-router-dom';

export function Logo({ to = '/' }: { to?: string }) {
  return (
    <Link to={to} className="inline-flex items-center gap-2 rounded-md font-semibold tracking-tight text-ink" aria-label="VeloceRide home">
      <svg viewBox="0 0 32 32" className="size-7" aria-hidden>
        <rect width="32" height="32" rx="8" className="fill-ink" />
        <path d="M8 9l8 15 8-15" fill="none" className="stroke-accent-bright" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="text-[15px]">
        Veloce<span className="text-accent">Ride</span>
      </span>
    </Link>
  );
}
