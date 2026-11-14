import { useMutation } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { userService } from '@/services/userService';
import { toast } from '@/store/toastStore';
import type { ActiveMode } from '@/types/dto';
import { getErrorMessage } from '@/utils/errors';

/**
 * Passenger/Rider mode. Stored server-side (PATCH /users/active-mode) as a UI preference only —
 * it never grants or removes permissions; the backend authorizes every action independently.
 */
export function useMode() {
  const mode: ActiveMode = useAuthStore((s) => s.user?.activeMode ?? 'PASSENGER');
  const setUser = useAuthStore((s) => s.setUser);
  const mutation = useMutation({
    mutationFn: (next: ActiveMode) => userService.switchMode(next),
    onSuccess: (user) => {
      setUser(user);
      toast.success(user.activeMode === 'RIDER' ? 'Switched to Rider mode' : 'Switched to Passenger mode');
    },
    onError: (err) => toast.error('Could not switch mode', getErrorMessage(err)),
  });
  return {
    mode,
    isRider: mode === 'RIDER',
    switchTo: (next: ActiveMode) => next !== mode && mutation.mutate(next),
    switching: mutation.isPending,
  };
}
