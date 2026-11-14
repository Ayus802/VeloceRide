import { beforeEach, describe, expect, it, vi } from 'vitest';

/** Minimal stand-in for @stomp/stompjs Client to test subscription lifecycle without a server. */
const instances: FakeClient[] = [];
class FakeClient {
  connected = false;
  active = false;
  connectHeaders: Record<string, string> = {};
  config: Record<string, (...args: never[]) => unknown>;
  subscriptions = new Map<string, { cb: (f: { body: string }) => void; unsubscribe: () => void }>();
  published: Array<{ destination: string; body: string }> = [];
  constructor(config: Record<string, (...args: never[]) => unknown>) {
    this.config = config;
    instances.push(this);
  }
  activate() {
    this.active = true;
  }
  async deactivate() {
    this.active = false;
    this.connected = false;
  }
  subscribe(destination: string, cb: (f: { body: string }) => void) {
    const sub = { cb, unsubscribe: vi.fn(() => this.subscriptions.delete(destination)) };
    this.subscriptions.set(destination, sub);
    return sub;
  }
  publish(msg: { destination: string; body: string }) {
    this.published.push(msg);
  }
  // test helpers
  async connect() {
    await (this.config.beforeConnect as () => Promise<void>)();
    this.connected = true;
    (this.config.onConnect as () => void)();
  }
  drop() {
    this.connected = false;
    this.subscriptions.clear();
    (this.config.onWebSocketClose as () => void)();
  }
  error(message: string) {
    (this.config.onStompError as (f: { headers: Record<string, string>; body: string }) => void)({ headers: { message }, body: '' });
  }
  emit(destination: string, body: unknown) {
    this.subscriptions.get(destination)?.cb({ body: JSON.stringify(body) });
  }
}
vi.mock('@stomp/stompjs', () => ({ Client: FakeClient }));

const { connectRealtime, disconnectRealtime, subscribe, publish, useRealtimeStore } = await import('@/services/realtime/stompClient');
const { useAuthStore } = await import('@/store/authStore');

describe('stompClient', () => {
  beforeEach(async () => {
    await disconnectRealtime();
    instances.length = 0;
    useAuthStore.setState({ status: 'authenticated', accessToken: 'not-a-jwt', refreshToken: 'r' });
  });

  it('authenticates CONNECT with the current bearer token', async () => {
    connectRealtime();
    await instances[0].connect();
    expect(instances[0].connectHeaders).toEqual({ Authorization: 'Bearer not-a-jwt' });
    expect(useRealtimeStore.getState().state).toBe('connected');
  });

  it('delivers parsed messages and re-subscribes after a reconnect', async () => {
    connectRealtime();
    const client = instances[0];
    const received: unknown[] = [];
    subscribe('/topic/rides/r-1/location', (b) => received.push(b));
    await client.connect();
    client.emit('/topic/rides/r-1/location', { latitude: 1, longitude: 2 });
    expect(received).toEqual([{ latitude: 1, longitude: 2 }]);

    client.drop();
    expect(useRealtimeStore.getState().state).toBe('reconnecting');
    await client.connect();
    client.emit('/topic/rides/r-1/location', { latitude: 3, longitude: 4 });
    expect(received).toHaveLength(2);
  });

  it('unsubscribes on cleanup', async () => {
    connectRealtime();
    await instances[0].connect();
    const off = subscribe('/topic/rides/r-1/status', () => {});
    expect(instances[0].subscriptions.has('/topic/rides/r-1/status')).toBe(true);
    off();
    expect(instances[0].subscriptions.has('/topic/rides/r-1/status')).toBe(false);
  });

  it('drops a destination the server refused so it is not retried forever', async () => {
    connectRealtime();
    const onDenied = vi.fn();
    subscribe('/topic/rides/r-9/location', () => {}, onDenied);
    await instances[0].connect();
    instances[0].error('You are not allowed to subscribe to /topic/rides/r-9/location');
    expect(onDenied).toHaveBeenCalled();
    instances[0].drop();
    await instances[0].connect();
    expect(instances[0].subscriptions.has('/topic/rides/r-9/location')).toBe(false);
  });

  it('publishes only while connected so callers can fall back to REST', async () => {
    connectRealtime();
    expect(publish('/app/location/update', { rideId: 'r-1' })).toBe(false);
    await instances[0].connect();
    expect(publish('/app/location/update', { rideId: 'r-1', latitude: 1, longitude: 2 })).toBe(true);
    expect(instances[0].published[0]).toEqual({ destination: '/app/location/update', body: '{"rideId":"r-1","latitude":1,"longitude":2}' });
  });
});
