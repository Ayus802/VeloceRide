import type { Ref } from 'react';
import { FieldShell } from '@/components/common/Field';
import { cn } from '@/utils/cn';

/**
 * 6-digit code field. One input (not six boxes) so paste, SMS autofill (`one-time-code`) and screen readers
 * all work; digits are spaced visually. Calls onComplete once 6 digits are entered.
 */
export function OtpInput({
  label,
  value,
  onChange,
  onComplete,
  error,
  disabled,
  autoFocus,
  ref,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  onComplete?: (v: string) => void;
  error?: string;
  disabled?: boolean;
  autoFocus?: boolean;
  ref?: Ref<HTMLInputElement>;
}) {
  return (
    <FieldShell label={label} error={error}>
      {({ id, describedBy, invalid }) => (
        <input
          id={id}
          ref={ref}
          value={value}
          disabled={disabled}
          autoFocus={autoFocus}
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="\d{6}"
          maxLength={6}
          placeholder="••••••"
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          onChange={(e) => {
            const digits = e.target.value.replace(/\D/g, '').slice(0, 6);
            onChange(digits);
            if (digits.length === 6) onComplete?.(digits);
          }}
          className={cn(
            'h-12 w-full rounded-lg border border-line bg-bg-2 px-4 text-center font-mono text-xl tracking-[0.5em] text-ink',
            'placeholder:text-faint placeholder:tracking-[0.5em] hover:border-line-strong focus:border-accent focus:outline-none',
            'focus:ring-2 focus:ring-accent/25 disabled:opacity-60 aria-[invalid=true]:border-danger/70',
          )}
        />
      )}
    </FieldShell>
  );
}
