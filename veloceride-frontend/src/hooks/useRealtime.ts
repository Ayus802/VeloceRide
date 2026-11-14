import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { qk } from '@/api/queryClient';
import { useAuthStore } from '@/store/authStore';
import { connectRealtime, disconnectRealtime, subscribe } from '@/services/realtime/stompClient';
import { toast } from '@/store/toastStore';
import type { AppNotification } from '@/types/dto';

/**
 * Opens the STOMP connection while signed in and listens to the private notification queue.
 * New notifications refresh the bell/list and related booking/ride data, and show a toast.
 */
export function useRealtimeSession() {
  const status = useAuthStore((s) => s.status);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (status !== 'authenticated') return;
    connectRealtime();
    const unsubscribe = subscribe('/user/queue/notifications', (body) => {
      const n = body as AppNotification;
      toast.info(n.title, n.message);
      void queryClient.invalidateQueries({ queryKey: qk.notifications });
      if (n.notificationType.startsWith('BOOKING') || n.notificationType.startsWith('RIDE')) {
        void queryClient.invalidateQueries({ queryKey: qk.bookings });
        void queryClient.invalidateQueries({ queryKey: qk.rides });
      }
      if (n.notificationType.startsWith('PAYMENT') || n.notificationType === 'SYSTEM') {
        void queryClient.invalidateQueries({ queryKey: qk.payments });
      }
      if (n.notificationType === 'NEW_REVIEW') {
        void queryClient.invalidateQueries({ queryKey: qk.me });
      }
    });
    return () => {
      unsubscribe();
    };
  }, [status, queryClient]);

  useEffect(() => {
    if (status === 'anonymous') void disconnectRealtime();
  }, [status]);
}
