import { useRealtimeStore, type ConnectionState } from '@/services/realtime/stompClient';
import { cn } from '@/utils/cn';

const LABEL: Record<ConnectionState, string> = {
  idle: 'Offline',
  connecting: 'Connecting…',
  connected: 'Live',
  reconnecting: 'Reconnecting…',
  error: 'Connection problem',
};

/** Small pill showing the STOMP connection state. */
export function ConnectionPill() {
  const state = useRealtimeStore((s) => s.state);
  const tone =
    state === 'connected' ? 'bg-success' : state === 'error' || state === 'idle' ? 'bg-danger' : 'bg-warning animate-pulse';
  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-line px-2.5 py-1 text-xs text-muted" role="status">
      <span className={cn('size-2 rounded-full', tone)} aria-hidden />
      {LABEL[state]}
    </span>
  );
}
