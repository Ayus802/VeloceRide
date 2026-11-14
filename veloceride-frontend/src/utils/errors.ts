import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

/** Normalised API error. `status` 0 = network failure / no response. */
export class AppError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  /** Envelope `data` on errors that carry a payload (e.g. 403 VERIFICATION_REQUIRED on login). */
  readonly data: unknown;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, data?: unknown) {
    super(message);
    this.name = 'AppError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.data = data;
  }

  get isNetwork() {
    return this.status === 0;
  }
}

export function isAppError(err: unknown): err is AppError {
  return err instanceof AppError;
}

export function errorStatus(err: unknown): number | undefined {
  return isAppError(err) ? err.status : undefined;
}

export function getErrorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (isAppError(err)) {
    if (err.isNetwork) return 'Cannot reach the server. Check your connection and try again.';
    return err.message || fallback;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

/**
 * Copies backend field errors onto a react-hook-form form.
 * Returns true when at least one error matched a form field (so callers can skip a generic toast).
 */
export function applyFieldErrors<T extends FieldValues>(
  err: unknown,
  setError: UseFormSetError<T>,
  fields: readonly string[],
): boolean {
  if (!isAppError(err)) return false;
  let matched = false;
  for (const [field, message] of Object.entries(err.fieldErrors)) {
    if (fields.includes(field)) {
      setError(field as Path<T>, { type: 'server', message: capitalize(message) });
      matched = true;
    }
  }
  return matched;
}

function capitalize(s: string) {
  return s ? s[0].toUpperCase() + s.slice(1) : s;
}
