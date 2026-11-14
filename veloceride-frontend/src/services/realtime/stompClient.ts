import { Client, type IFrame, type StompSubscription } from '@stomp/stompjs';
import { create } from 'zustand';
import { WS_URL } from '@/constants/config';
import { refreshSession } from '@/api/apiClient';
import { secondsUntilExpiry } from '@/api/tokenStorage';
import { useAuthStore } from '@/store/authStore';

/**
 * One STOMP connection per signed-in browser tab (backend: /ws, see docs/WEBSOCKET_MAPPING.md).
 * - CONNECT carries the current access token; it is refreshed first if about to expire.
 * - Subscriptions are registered here and re-applied after every reconnect.
 * - A refused SUBSCRIBE makes the server close the connection, so a destination named in an
 *   ERROR frame is dropped from the registry (and its owner told) to avoid a reconnect loop.
 */
export type ConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'error';

export const useRealtimeStore = create<{ state: ConnectionState; set: (s: ConnectionState) => void }>((set) => ({
  state: 'idle',
  set: (state) => set({ state }),
}));

type Handler = (body: unknown) => void;
interface Registration {
  destination: string;
  onMessage: Handler;
  onDenied?: (message: string) => void;
  handle?: StompSubscription;
}

const registry = new Set<Registration>();
let client: Client | null = null;

const setState = (s: ConnectionState) => useRealtimeStore.getState().set(s);

function attach(reg: Registration) {
  if (!client?.connected) return;
  reg.handle = client.subscribe(reg.destination, (frame) => {
    try {
      reg.onMessage(JSON.parse(frame.body));
    } catch {
      /* ignore malformed frames */
    }
  });
}

function handleStompError(frame: IFrame) {
  const message = frame.headers['message'] ?? frame.body ?? 'Real-time connection error';
  for (const reg of [...registry]) {
    if (message.includes(reg.destination)) {
      registry.delete(reg);
      reg.onDenied?.(message);
    }
  }
  setState('error');
}

let hasConnected = false;

export function connectRealtime() {
  if (client?.active) return;
  hasConnected = false;
  client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 5_000,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
    beforeConnect: async () => {
      const self = client;
      if (!self) return;
      setState(hasConnected ? 'reconnecting' : 'connecting');
      let token = useAuthStore.getState().accessToken;
      const ttl = token ? secondsUntilExpiry(token) : null;
      if (token && ttl !== null && ttl < 30) {
        token = await refreshSession(token);
      }
      if (!token) {
        // Signed out meanwhile: stop instead of looping with a missing token.
        void self.deactivate();
        setState('idle');
        return;
      }
      self.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    onConnect: () => {
      hasConnected = true;
      setState('connected');
      registry.forEach(attach);
    },
    onStompError: handleStompError,
    onWebSocketClose: () => {
      registry.forEach((r) => (r.handle = undefined));
      if (client?.active) setState('reconnecting');
    },
  });
  client.activate();
}

export async function disconnectRealtime() {
  registry.clear();
  const c = client;
  client = null;
  setState('idle');
  await c?.deactivate();
}

/** Registers a subscription that survives reconnects. Returns an unsubscribe function. */
export function subscribe(destination: string, onMessage: Handler, onDenied?: (message: string) => void) {
  const reg: Registration = { destination, onMessage, onDenied };
  registry.add(reg);
  attach(reg);
  return () => {
    registry.delete(reg);
    try {
      if (client?.connected) reg.handle?.unsubscribe();
    } catch {
      /* connection already gone */
    }
  };
}

/** Sends to an /app destination. Returns false when not connected (caller may use a REST fallback). */
export function publish(destination: string, body: unknown): boolean {
  if (!client?.connected) return false;
  client.publish({ destination, body: JSON.stringify(body) });
  return true;
}

export function isRealtimeConnected() {
  return Boolean(client?.connected);
}
