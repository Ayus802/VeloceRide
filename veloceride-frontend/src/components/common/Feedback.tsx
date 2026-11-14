import type { ReactNode } from 'react';
import { AlertTriangle, Loader2, Lock, SearchX, WifiOff } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { getErrorMessage, isAppError } from '@/utils/errors';
import { cn } from '@/utils/cn';

export function Spinner({ className, label = 'Loading' }: { className?: string; label?: string }) {
  return (
    <span role="status" aria-label={label} className={cn('inline-flex text-muted', className)}>
      <Loader2 className="size-5 animate-spin" aria-hidden />
    </span>
  );
}

export function PageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner label={label} />
    </div>
  );
}

/** Skeleton rows for list views. */
export function ListSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="divide-y divide-line rounded-xl border border-line" aria-hidden>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-4 p-4">
          <div className="h-10 w-10 animate-pulse rounded-full bg-surface" />
          <div className="flex-1 space-y-2">
            <div className="h-3 w-1/3 animate-pulse rounded bg-surface" />
            <div className="h-3 w-1/2 animate-pulse rounded bg-surface" />
          </div>
        </div>
      ))}
    </div>
  );
}

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center px-6 py-14 text-center', className)}>
      <div className="mb-4 text-faint">{icon ?? <SearchX className="size-8" aria-hidden />}</div>
      <p className="font-medium text-ink">{title}</p>
      {description && <p className="mt-1 max-w-sm text-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

/** Renders the right message for an API failure (offline, forbidden, not found, other). */
export function ErrorState({ error, onRetry, className }: { error: unknown; onRetry?: () => void; className?: string }) {
  const status = isAppError(error) ? error.status : undefined;
  if (status === 403) {
    return (
      <EmptyState
        className={className}
        icon={<Lock className="size-8" aria-hidden />}
        title="You don’t have access to this"
        description={getErrorMessage(error)}
      />
    );
  }
  if (status === 404) {
    return <EmptyState className={className} title="Not found" description={getErrorMessage(error)} />;
  }
  return (
    <EmptyState
      className={className}
      icon={status === 0 ? <WifiOff className="size-8" aria-hidden /> : <AlertTriangle className="size-8" aria-hidden />}
      title={status === 0 ? 'You seem to be offline' : 'Couldn’t load this'}
      description={getErrorMessage(error)}
      action={
        onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry}>
            Try again
          </Button>
        )
      }
    />
  );
}

export function Notice({
  tone = 'info',
  children,
  className,
}: {
  tone?: 'info' | 'warning' | 'danger' | 'success';
  children: ReactNode;
  className?: string;
}) {
  const tones = {
    info: 'border-line bg-bg-2 text-muted',
    warning: 'border-warning/30 bg-warning/5 text-warning',
    danger: 'border-danger/30 bg-danger-soft text-danger',
    success: 'border-success/30 bg-success/5 text-success',
  };
  return <div className={cn('rounded-lg border px-4 py-3 text-[13px] leading-relaxed', tones[tone], className)}>{children}</div>;
}
