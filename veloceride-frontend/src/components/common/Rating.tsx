import { Star } from 'lucide-react';
import { useState } from 'react';
import { formatRating } from '@/utils/format';
import { cn } from '@/utils/cn';

/** Compact "★ 4.8 (23)" display. */
export function RatingInline({ value, count, className }: { value?: number; count?: number; className?: string }) {
  if (value == null) {
    return <span className={cn('text-[13px] text-faint', className)}>No reviews yet</span>;
  }
  return (
    <span className={cn('inline-flex items-center gap-1 text-[13px] text-muted', className)}>
      <Star className="size-3.5 fill-warning text-warning" aria-hidden />
      <span className="font-medium text-ink">{formatRating(value)}</span>
      {count != null && <span>({count})</span>}
      <span className="sr-only">out of 5</span>
    </span>
  );
}

export function Stars({ value, size = 'sm' }: { value: number; size?: 'sm' | 'md' }) {
  const cls = size === 'sm' ? 'size-3.5' : 'size-5';
  return (
    <span className="inline-flex gap-0.5" role="img" aria-label={`${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Star key={i} className={cn(cls, i <= value ? 'fill-warning text-warning' : 'text-line-strong')} aria-hidden />
      ))}
    </span>
  );
}

/** Keyboard-accessible 1–5 rating picker (radio group). */
export function RatingInput({
  value,
  onChange,
  error,
}: {
  value: number;
  onChange: (v: number) => void;
  error?: string;
}) {
  const [hover, setHover] = useState(0);
  const shown = hover || value;
  const labels = ['', 'Poor', 'Fair', 'Good', 'Very good', 'Excellent'];
  return (
    <div>
      <div role="radiogroup" aria-label="Rating" className="flex items-center gap-1" onMouseLeave={() => setHover(0)}>
        {[1, 2, 3, 4, 5].map((i) => (
          <button
            key={i}
            type="button"
            role="radio"
            aria-checked={value === i}
            aria-label={`${i} star${i > 1 ? 's' : ''}`}
            onMouseEnter={() => setHover(i)}
            onClick={() => onChange(i)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowRight' || e.key === 'ArrowUp') onChange(Math.min(5, (value || 0) + 1));
              if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') onChange(Math.max(1, (value || 2) - 1));
            }}
            tabIndex={value === i || (!value && i === 1) ? 0 : -1}
            className="rounded-md p-1"
          >
            <Star className={cn('size-7 transition-colors', i <= shown ? 'fill-warning text-warning' : 'text-line-strong')} aria-hidden />
          </button>
        ))}
        <span className="ml-2 text-[13px] text-muted">{labels[shown]}</span>
      </div>
      {error && (
        <p role="alert" className="mt-1 text-[13px] text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
