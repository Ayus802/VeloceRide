import { useCallback, useEffect, useRef, useState } from 'react';
import { LOCATION_PUBLISH_INTERVAL_MS } from '@/constants/config';
import { publish, subscribe } from '@/services/realtime/stompClient';
import { locationService } from '@/services/locationService';
import { getErrorMessage, isAppError } from '@/utils/errors';

export type SharingState = 'off' | 'requesting' | 'sharing' | 'denied' | 'unavailable' | 'error';

interface Position {
  latitude: number;
  longitude: number;
  accuracy: number;
  at: number;
}

/**
 * Driver-side GPS sharing for an ONGOING ride.
 * - Uses the browser Geolocation API (asks permission on start).
 * - Publishes at most one update every 4 s to /app/location/update; falls back to REST when the
 *   socket is down. Timestamp is omitted so the server's clock is used.
 * - Stops on `stop()`, when `active` becomes false (ride completed/cancelled) and on unmount.
 */
export function useLocationSharing(rideId: string | undefined, active: boolean) {
  const [state, setState] = useState<SharingState>('off');
  const [position, setPosition] = useState<Position | null>(null);
  const [lastSentAt, setLastSentAt] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const watchId = useRef<number | null>(null);
  const lastPublish = useRef(0);

  const stop = useCallback(() => {
    if (watchId.current != null) navigator.geolocation?.clearWatch(watchId.current);
    watchId.current = null;
    setState((s) => (s === 'sharing' || s === 'requesting' ? 'off' : s));
  }, []);

  const send = useCallback(
    async (lat: number, lng: number) => {
      if (!rideId) return;
      const now = Date.now();
      if (now - lastPublish.current < LOCATION_PUBLISH_INTERVAL_MS) return;
      lastPublish.current = now;
      const body = { rideId, latitude: lat, longitude: lng };
      if (publish('/app/location/update', body)) {
        setLastSentAt(now);
        return;
      }
      try {
        await locationService.publish(rideId, lat, lng);
        setLastSentAt(now);
      } catch (err) {
        if (isAppError(err) && err.status === 429) return; // rate limited; next tick will go through
        setError(getErrorMessage(err));
      }
    },
    [rideId],
  );

  const start = useCallback(() => {
    if (!rideId) return;
    if (!('geolocation' in navigator)) {
      setState('unavailable');
      setError('This browser does not support location sharing.');
      return;
    }
    setError(null);
    setState('requesting');
    if (watchId.current != null) navigator.geolocation.clearWatch(watchId.current);
    watchId.current = navigator.geolocation.watchPosition(
      (pos) => {
        setState('sharing');
        const p = { latitude: pos.coords.latitude, longitude: pos.coords.longitude, accuracy: pos.coords.accuracy, at: Date.now() };
        setPosition(p);
        void send(p.latitude, p.longitude);
      },
      (err) => {
        if (err.code === err.PERMISSION_DENIED) {
          setState('denied');
          setError('Location permission was denied. Allow it in your browser settings to share your position.');
          if (watchId.current != null) navigator.geolocation.clearWatch(watchId.current);
          watchId.current = null;
        } else {
          setState('error');
          setError(err.message || 'Could not read your location.');
        }
      },
      { enableHighAccuracy: true, maximumAge: 3_000, timeout: 15_000 },
    );
  }, [rideId, send]);

  // Server-side rejections of STOMP sends arrive privately on /user/queue/errors.
  useEffect(() => {
    if (!rideId || !active) return;
    return subscribe('/user/queue/errors', (body) => {
      const msg = (body as { message?: string }).message ?? 'Location update rejected';
      if (/limited to one/i.test(msg)) return;
      setError(msg);
    });
  }, [rideId, active]);

  useEffect(() => {
    if (!active) stop();
  }, [active, stop]);

  useEffect(() => stop, [stop]);

  return { state, position, lastSentAt, error, start, stop };
}
