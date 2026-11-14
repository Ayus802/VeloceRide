import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { PageLoader, EmptyState } from '@/components/common/Feedback';
import { Lock } from 'lucide-react';
import { loginRedirect, safeReturnTo } from '@/constants/routes';

/** Requires a signed-in user. Waits for session restore instead of flashing the login page. */
export function ProtectedRoute({ children }: { children?: ReactNode }) {
  const status = useAuthStore((s) => s.status);
  const location = useLocation();
  if (status === 'booting') return <PageLoader label="Restoring your session" />;
  if (status === 'anonymous') return <Navigate to={loginRedirect(location.pathname, location.search)} replace />;
  return children ?? <Outlet />;
}

/** Login/register pages: signed-in users go to their destination. */
export function GuestRoute() {
  const status = useAuthStore((s) => s.status);
  const location = useLocation();
  if (status === 'booting') return <PageLoader />;
  if (status === 'authenticated') {
    const returnTo = new URLSearchParams(location.search).get('returnTo');
    return <Navigate to={safeReturnTo(returnTo)} replace />;
  }
  return <Outlet />;
}

/** Navigation convenience only — the backend enforces ADMIN on every /api/admin call. */
export function AdminRoute() {
  const user = useAuthStore((s) => s.user);
  if (user?.role !== 'ADMIN') {
    return (
      <EmptyState
        icon={<Lock className="size-8" aria-hidden />}
        title="Administrators only"
        description="Your account does not have access to the admin area."
      />
    );
  }
  return <Outlet />;
}
