import { NavLink, Outlet } from 'react-router-dom';
import { ArrowLeft, BarChart3, Car, ClipboardList, MessageSquareWarning, Route, Users } from 'lucide-react';
import { AppHeader, Footer, PublicHeader } from '@/components/layout/Header';
import { Logo } from '@/components/layout/Logo';
import { ThemeMenu } from '@/components/common/ThemeControls';
import { useAuthStore } from '@/store/authStore';
import { useRealtimeSession } from '@/hooks/useRealtime';
import { cn } from '@/utils/cn';

/** Public pages: compact header; shows the app header instead when signed in. */
export function PublicLayout() {
  const status = useAuthStore((s) => s.status);
  useRealtimeSession();
  return (
    <div className="flex min-h-dvh flex-col">
      {status === 'authenticated' ? <AppHeader /> : <PublicHeader />}
      <main id="main" className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}

export function AppLayout() {
  useRealtimeSession();
  return (
    <div className="flex min-h-dvh flex-col">
      <AppHeader />
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 sm:py-10">
        <Outlet />
      </main>
    </div>
  );
}

/** Centered narrow column for auth forms. */
export function AuthLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-4 sm:px-6">
        <Logo />
        <ThemeMenu />
      </div>
      <main id="main" className="flex flex-1 items-start justify-center px-4 pb-16 pt-6 sm:pt-16">
        <div className="w-full max-w-sm">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

const adminNav = [
  { to: '/admin', label: 'Overview', icon: BarChart3, end: true },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/vehicles', label: 'Vehicles', icon: Car },
  { to: '/admin/rides', label: 'Rides', icon: Route },
  { to: '/admin/bookings', label: 'Bookings', icon: ClipboardList },
  { to: '/admin/reviews', label: 'Reviews', icon: MessageSquareWarning },
];

export function AdminLayout() {
  useRealtimeSession();
  return (
    <div className="flex min-h-dvh flex-col">
      <AppHeader />
      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6 md:flex-row md:gap-10 md:py-10">
        <aside className="md:w-48 md:shrink-0">
          <p className="mb-2 hidden px-3 text-xs font-medium uppercase tracking-wider text-faint md:block">Admin</p>
          <nav aria-label="Admin" className="-mx-4 flex gap-1 overflow-x-auto px-4 md:mx-0 md:flex-col md:px-0">
            {adminNav.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  cn(
                    'flex shrink-0 items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                    isActive ? 'bg-surface text-ink' : 'text-muted hover:text-ink',
                  )
                }
              >
                <Icon className="size-4" aria-hidden />
                {label}
              </NavLink>
            ))}
            <NavLink
              to="/dashboard"
              className="flex shrink-0 items-center gap-2.5 rounded-lg px-3 py-2 text-sm text-faint hover:text-ink md:mt-4"
            >
              <ArrowLeft className="size-4" aria-hidden />
              Back to app
            </NavLink>
          </nav>
        </aside>
        <main id="main" className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
