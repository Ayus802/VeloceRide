import { useEffect, useRef, useState } from 'react';
import { GOOGLE_CLIENT_ID } from '@/constants/config';
import { useTheme } from '@/theme/useTheme';

/** Minimal typing for Google Identity Services (https://developers.google.com/identity/gsi/web). */
interface GsiApi {
  accounts: {
    id: {
      initialize: (config: {
        client_id: string;
        callback: (response: { credential?: string }) => void;
        ux_mode?: 'popup' | 'redirect';
        auto_select?: boolean;
        cancel_on_tap_outside?: boolean;
        use_fedcm_for_prompt?: boolean;
      }) => void;
      renderButton: (parent: HTMLElement, options: Record<string, string | number>) => void;
      disableAutoSelect: () => void;
    };
  };
}
declare global {
  interface Window {
    google?: GsiApi;
  }
}

const GSI_SRC = 'https://accounts.google.com/gsi/client';
let loader: Promise<GsiApi> | null = null;

function loadGsi(): Promise<GsiApi> {
  if (window.google?.accounts?.id) return Promise.resolve(window.google);
  if (!loader) {
    loader = new Promise<GsiApi>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = GSI_SRC;
      script.async = true;
      script.defer = true;
      script.onload = () => (window.google ? resolve(window.google) : reject(new Error('Google sign-in failed to load')));
      script.onerror = () => {
        loader = null;
        reject(new Error('Google sign-in failed to load'));
      };
      document.head.appendChild(script);
    });
  }
  return loader;
}

export const googleSignInEnabled = Boolean(GOOGLE_CLIENT_ID);

/**
 * Google's official "Continue with Google" button (Google Identity Services, popup mode).
 * Google returns an ID token in the browser; it is sent to POST /api/auth/google where the backend
 * verifies signature, audience and expiry. Renders nothing when VITE_GOOGLE_CLIENT_ID is not set.
 */
export function GoogleSignInButton({
  onCredential,
  text = 'continue_with',
  disabled,
}: {
  onCredential: (idToken: string) => void;
  text?: 'signin_with' | 'signup_with' | 'continue_with';
  disabled?: boolean;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onCredential);
  callbackRef.current = onCredential;
  const { resolved } = useTheme();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!googleSignInEnabled) return;
    let cancelled = false;
    loadGsi()
      .then((gsi) => {
        if (cancelled || !containerRef.current) return;
        gsi.accounts.id.initialize({
          client_id: GOOGLE_CLIENT_ID,
          callback: (res) => res.credential && callbackRef.current(res.credential),
          ux_mode: 'popup',
          auto_select: false,
          cancel_on_tap_outside: true,
        });
        containerRef.current.innerHTML = '';
        gsi.accounts.id.renderButton(containerRef.current, {
          type: 'standard',
          theme: resolved === 'dark' ? 'filled_black' : 'outline',
          size: 'large',
          shape: 'rectangular',
          text,
          logo_alignment: 'center',
          width: Math.min(400, containerRef.current.offsetWidth || 384),
        });
      })
      .catch(() => !cancelled && setFailed(true));
    return () => {
      cancelled = true;
    };
  }, [resolved, text]);

  if (!googleSignInEnabled) return null;
  if (failed) {
    return <p className="text-center text-[13px] text-faint">Google sign-in is unavailable right now.</p>;
  }
  return (
    <div
      ref={containerRef}
      className={disabled ? 'pointer-events-none flex min-h-10 justify-center opacity-60' : 'flex min-h-10 justify-center'}
      aria-busy={disabled || undefined}
    />
  );
}

/** "or" divider shown between Google and the email form. */
export function OrDivider() {
  if (!googleSignInEnabled) return null;
  return (
    <div className="my-6 flex items-center gap-3 text-[13px] text-faint" role="separator">
      <span className="h-px flex-1 bg-line" />
      or continue with email
      <span className="h-px flex-1 bg-line" />
    </div>
  );
}
