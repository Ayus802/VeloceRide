import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { startSession } from '@/services/session';
import { safeReturnTo } from '@/constants/routes';
import type { AuthFlowResponse, GoogleSignup, VerificationChallenge } from '@/types/dto';
import { getErrorMessage, isAppError } from '@/utils/errors';
import { authService } from '@/services/authService';

/**
 * In-progress sign-up / sign-in state (verification challenge or pending Google sign-up).
 * Kept in sessionStorage so a reload on /verify doesn't lose it; it never contains passwords or session tokens.
 */
const KEY = 'veloceride.authflow';

export interface PendingAuthFlow {
  verification?: VerificationChallenge;
  googleSignup?: GoogleSignup;
  returnTo?: string;
  /** Epoch ms when saved; tokens inside expire server-side anyway. */
  savedAt: number;
}

export const pendingFlow = {
  read(): PendingAuthFlow | null {
    try {
      const raw = sessionStorage.getItem(KEY);
      return raw ? (JSON.parse(raw) as PendingAuthFlow) : null;
    } catch {
      return null;
    }
  },
  write(flow: Omit<PendingAuthFlow, 'savedAt'>) {
    try {
      sessionStorage.setItem(KEY, JSON.stringify({ ...flow, savedAt: Date.now() }));
    } catch {
      /* storage blocked: flow continues in memory via navigation state */
    }
  },
  clear() {
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
  },
};

/** Extracts the challenge from a 403 VERIFICATION_REQUIRED login error, if that's what it is. */
export function verificationFromError(err: unknown): AuthFlowResponse | null {
  if (!isAppError(err) || err.status !== 403) return null;
  const data = err.data as AuthFlowResponse | undefined;
  return data?.status === 'VERIFICATION_REQUIRED' && data.verification ? data : null;
}

/** Routes any multi-step auth result: sign in, go to code entry, or go to "add your phone". */
export function useAuthFlow() {
  const navigate = useNavigate();
  return useCallback(
    (flow: AuthFlowResponse, returnTo?: string | null) => {
      const target = returnTo ?? undefined;
      if (flow.status === 'AUTHENTICATED' && flow.auth) {
        pendingFlow.clear();
        startSession(flow.auth);
        navigate(safeReturnTo(target), { replace: true });
      } else if (flow.status === 'VERIFICATION_REQUIRED' && flow.verification) {
        pendingFlow.write({ verification: flow.verification, returnTo: target });
        navigate('/verify', { replace: true });
      } else if (flow.status === 'PHONE_REQUIRED' && flow.googleSignup) {
        pendingFlow.write({ googleSignup: flow.googleSignup, returnTo: target });
        navigate('/complete-signup', { replace: true });
      }
    },
    [navigate],
  );
}

/** Sends a Google ID token to the backend and routes the result. */
export function useGoogleSignIn(returnTo?: string | null) {
  const handleFlow = useAuthFlow();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const onCredential = useCallback(
    async (idToken: string) => {
      setBusy(true);
      setError(null);
      try {
        handleFlow(await authService.google(idToken), returnTo);
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setBusy(false);
      }
    },
    [handleFlow, returnTo],
  );
  return { onCredential, busy, error };
}
