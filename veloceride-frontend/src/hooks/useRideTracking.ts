import { useEffect, useState } from 'react';
import { subscribe } from '@/services/realtime/stompClient';
import { locationService } from '@/services/locationService';
import type { LocationUpdate, RideStatus, TrackingStatusMessage } from '@/types/dto';

interface TrackingState {
  location: LocationUpdate | null;
  /** Latest status pushed on /topic/rides/{id}/status (null until something is received). */
  pushedStatus: RideStatus | null;
  trackingActive: boolean | null;
  denied: string | null;
}

/**
 * Live driver location for a ride. Only call with `enabled` once the caller has confirmed
 * eligibility (driver, or passenger with an ACCEPTED booking while the ride is SCHEDULED/ONGOING):
 * an unauthorized SUBSCRIBE makes the server drop the whole STOMP connection.
 */
export function useRideTracking(rideId: string | undefined, enabled: boolean, seedLatest: boolean) {
  const [state, setState] = useState<TrackingState>({ location: null, pushedStatus: null, trackingActive: null, denied: null });

  useEffect(() => {
    if (!rideId || !enabled) return;
    let cancelled = false;

    if (seedLatest) {
      // Initial snapshot; 404 (nothing shared yet) and 409 (not ongoing) are expected and ignored.
      locationService
        .latest(rideId)
        .then((loc) => {
          if (!cancelled) setState((s) => (s.location ? s : { ...s, location: loc }));
        })
        .catch(() => {});
    }

    const onDenied = (message: string) => setState((s) => ({ ...s, denied: message }));
    const offLocation = subscribe(
      `/topic/rides/${rideId}/location`,
      (body) => setState((s) => ({ ...s, location: body as LocationUpdate })),
      onDenied,
    );
    const offStatus = subscribe(
      `/topic/rides/${rideId}/status`,
      (body) => {
        const msg = body as TrackingStatusMessage;
        setState((s) => ({ ...s, pushedStatus: msg.rideStatus, trackingActive: msg.trackingActive }));
      },
      onDenied,
    );
    return () => {
      cancelled = true;
      offLocation();
      offStatus();
    };
  }, [rideId, enabled, seedLatest]);

  return state;
}
