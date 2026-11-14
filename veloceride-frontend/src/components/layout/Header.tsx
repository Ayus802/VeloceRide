import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Bell,
  Car,
  ClipboardList,
  Inbox,
  LayoutDashboard,
  LogOut,
  Menu,
  Plus,
  Search,
  Settings,
  Shield,
  Star,
  User,
  X,
} from 'lucide-react';
import { Logo } from '@/components/layout/Logo';
import { Avatar } from '@/components/common/Avatar';
import { buttonClass, IconButton } from '@/components/common/Button';
import { Dropdown, DropdownItem, DropdownLabel, DropdownSeparator } from '@/components/dropdowns/Dropdown';
import { Segmented } from '@/components/common/Navigation';
import { ThemeMenu, ThemeSegmented } from '@/components/common/ThemeControls';
import { useAuthStore } from '@/store/authStore';
import { useMode } from '@/hooks/useMode';
import { notificationService } from '@/services/notificationService';
import { endSession } from '@/services/session';
import { qk } from '@/api/queryClient';
import { useRealtimeStore } from '@/services/realtime/stompClient';
import { toast } from '@/store/toastStore';
import { notificationTarget } from '@/utils/notificationTarget';
import { getErrorMessage } from '@/utils/errors';
import { timeAgo } from '@/utils/format';
import { cn } from '@/utils/cn';

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn('rounded-lg px-3 py-2 text-sm font-medium transition-colors', isActive ? 'bg-surface text-ink' : 'text-muted hover:text-ink');

const headerShell = 'sticky top-0 z-30 border-b border-line/70 bg-bg/80 backdrop-blur-md supports-[backdrop-filter]:bg-bg/70';

function useMainNav() {
  const { isRider } = useMode();
  return [
    { to: '/dashboard', label: 'Dashboard', icon: <LayoutDashboard className="size-4" /> },
    { to: '/rides/search', label: 'Find a ride', icon: <Search className="size-4" /> },
    isRider
      ? { to: '/my-rides', label: 'My rides', icon: <Car className="size-4" /> }
      : { to: '/bookings', label: 'My trips', icon: <ClipboardList className="size-4" /> },
  ];
}

/** Header for visitors (landing page and public ride pages). */
export function PublicHeader() {
  return (
    <header className={headerShell}>
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <Logo />
        <nav className="flex items-center gap-1" aria-label="Main">
          <NavLink to="/rides/search" className={(s) => cn(navLinkClass(s), 'hidden md:inline-flex')}>
            Find a ride
          </NavLink>
          <a href="/#how-it-works" className="hidden rounded-lg px-3 py-2 text-sm font-medium text-muted transition-colors hover:text-ink md:inline-flex">
            How it works
          </a>
          <span className="mx-1 hidden h-5 w-px bg-line md:block" aria-hidden />
          <ThemeMenu />
          <Link to="/login" className={buttonClass('ghost', 'sm')}>
            Log in
          </Link>
          <Link to="/register" className={buttonClass('primary', 'sm')}>
            Sign up
          </Link>
        </nav>
      </div>
    </header>
  );
}

/** Bell with a popover of the latest notifications (real data from /api/notifications). */
function NotificationBell() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const connected = useRealtimeStore((s) => s.state === 'connected');
  const unread = useQuery({
    queryKey: qk.unreadCount,
    queryFn: notificationService.unreadCount,
    refetchInterval: connected ? false : 60_000,
  });
  const latest = useQuery({
    queryKey: qk.notificationList({ size: 5 }),
    queryFn: () => notificationService.list({ size: 5 }),
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: qk.notifications });
  const markAll = useMutation({ mutationFn: notificationService.markAllRead, onSuccess: invalidate });
  const count = unread.data?.unreadCount ?? 0;
  const items = latest.data?.content ?? [];

  const open = async (id: string) => {
    const n = items.find((i) => i.id === id);
    if (!n) return;
    if (!n.isRead) notificationService.markRead(n.id).then(invalidate).catch(() => {});
    try {
      const target = await notificationTarget(n);
      navigate(target ?? '/notifications');
    } catch (err) {
      toast.error('Couldn’t open this item', getErrorMessage(err));
    }
  };

  return (
    <Dropdown
      menuClassName="w-[min(22rem,calc(100vw-2rem))] p-0"
      trigger={(props) => (
        <button
          type="button"
          aria-label={count ? `Notifications, ${count} unread` : 'Notifications'}
          className="relative inline-flex size-9 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface hover:text-ink aria-expanded:bg-surface aria-expanded:text-ink"
          {...props}
        >
          <Bell className="size-[18px]" aria-hidden />
          {count > 0 && (
            <span className="absolute right-1 top-1 flex min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[10px] font-semibold leading-4 text-accent-ink">
              {count > 99 ? '99+' : count}
            </span>
          )}
        </button>
      )}
    >
      <div className="flex items-center justify-between border-b border-line px-4 py-3">
        <span className="font-semibold text-ink">Notifications</span>
        {count > 0 && (
          <button type="button" onClick={() => markAll.mutate()} className="text-[13px] text-accent hover:underline" disabled={markAll.isPending}>
            Mark all as read
          </button>
        )}
      </div>
      <div className="max-h-96 overflow-y-auto p-1">
        {latest.isPending ? (
          <p className="px-3 py-6 text-center text-[13px] text-muted">Loading…</p>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center px-3 py-8 text-center text-muted">
            <Inbox className="mb-2 size-6 text-faint" aria-hidden />
            <p className="text-[13px]">You’re all caught up.</p>
          </div>
        ) : (
          items.map((n) => (
            <DropdownItem key={n.id} onSelect={() => void open(n.id)}>
              <span className={cn('mt-1.5 size-2 shrink-0 self-start rounded-full', n.isRead ? 'bg-transparent' : 'bg-accent-bright')} aria-hidden />
              <span className="min-w-0 flex-1">
                <span className={cn('block truncate', n.isRead ? 'text-muted' : 'font-medium text-ink')}>{n.title}</span>
                <span className="line-clamp-2 block text-[13px] text-muted">{n.message}</span>
                <span className="mt-0.5 block text-xs text-faint">{timeAgo(n.createdAt)}</span>
              </span>
            </DropdownItem>
          ))
        )}
      </div>
      <div className="border-t border-line p-1">
        <DropdownItem to="/notifications">
          <span className="w-full text-center text-[13px] text-accent">View all notifications</span>
        </DropdownItem>
      </div>
    </Dropdown>
  );
}

function ModeSwitch() {
  const { mode, switchTo, switching } = useMode();
  return (
    <div className="px-3 pb-2" aria-busy={switching}>
      <Segmented
        label="Mode"
        value={mode}
        onChange={switchTo}
        options={[
          { value: 'PASSENGER', label: 'Passenger' },
          { value: 'RIDER', label: 'Rider' },
        ]}
      />
    </div>
  );
}

function ProfileMenu() {
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  const { isRider } = useMode();
  if (!user) return null;
  return (
    <Dropdown
      trigger={(props) => (
        <button type="button" className="rounded-full" aria-label="Account menu" {...props}>
          <Avatar name={user.fullName} src={user.profilePicture} size="sm" />
        </button>
      )}
      menuClassName="w-72"
    >
      <div className="px-3 pb-2 pt-2">
        <p className="truncate font-medium text-ink">{user.fullName}</p>
        <p className="truncate text-[13px] text-muted">{user.email}</p>
      </div>
      <DropdownLabel>Mode</DropdownLabel>
      <ModeSwitch />
      <DropdownLabel>Appearance</DropdownLabel>
      <div className="px-3 pb-2">
        <ThemeSegmented />
      </div>
      <DropdownSeparator />
      <DropdownItem to="/profile" icon={<User />}>
        Profile
      </DropdownItem>
      <DropdownItem to="/reviews" icon={<Star />}>
        Reviews
      </DropdownItem>
      <DropdownItem to="/vehicles" icon={<Car />}>
        Vehicles
      </DropdownItem>
      {isRider ? (
        <DropdownItem to="/bookings" icon={<ClipboardList />}>
          My trips
        </DropdownItem>
      ) : (
        <DropdownItem to="/my-rides" icon={<Car />}>
          My rides
        </DropdownItem>
      )}
      <DropdownItem to="/requests" icon={<ClipboardList />}>
        Booking requests
      </DropdownItem>
      <DropdownItem to="/settings" icon={<Settings />}>
        Settings
      </DropdownItem>
      {user.role === 'ADMIN' && (
        <DropdownItem to="/admin" icon={<Shield />}>
          Admin
        </DropdownItem>
      )}
      <DropdownSeparator />
      <DropdownItem
        icon={<LogOut />}
        onSelect={async () => {
          await endSession();
          navigate('/', { replace: true });
        }}
      >
        Log out
      </DropdownItem>
    </Dropdown>
  );
}

export function AppHeader() {
  const nav = useMainNav();
  const { isRider } = useMode();
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setMobileOpen(false), [location.pathname]);

  return (
    <header className={headerShell}>
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-3 px-4 sm:px-6">
        <IconButton
          label={mobileOpen ? 'Close menu' : 'Open menu'}
          className="-ml-2 md:hidden"
          onClick={() => setMobileOpen((o) => !o)}
          aria-expanded={mobileOpen}
        >
          {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
        </IconButton>
        <Logo to="/dashboard" />
        <nav className="ml-4 hidden items-center gap-1 md:flex" aria-label="Main">
          {nav.map((n) => (
            <NavLink key={n.to} to={n.to} className={navLinkClass}>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-1.5">
          {isRider && (
            <Link to="/rides/new" className={buttonClass('secondary', 'sm', 'mr-1 hidden sm:inline-flex')}>
              <Plus className="size-4" aria-hidden />
              Publish
            </Link>
          )}
          <NotificationBell />
          <ProfileMenu />
        </div>
      </div>
      {mobileOpen && (
        <nav className="border-t border-line px-4 py-3 md:hidden" aria-label="Main">
          {[...nav, { to: '/rides/new', label: 'Publish a ride', icon: <Plus className="size-4" /> }].map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              className={({ isActive }) =>
                cn('flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm', isActive ? 'bg-surface text-ink' : 'text-muted')
              }
            >
              {n.icon}
              {n.label}
            </NavLink>
          ))}
        </nav>
      )}
    </header>
  );
}

export function Footer() {
  const signedIn = useAuthStore((s) => s.status === 'authenticated');
  const links = [
    { to: '/', label: 'Home' },
    { to: '/rides/search', label: 'Find a ride' },
    { to: '/rides/new', label: 'Publish a ride' },
    ...(signedIn
      ? []
      : [
          { to: '/login', label: 'Log in' },
          { to: '/register', label: 'Sign up' },
        ]),
  ];
  return (
    <footer className="border-t border-line/70">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-8 text-[13px] text-faint sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <div className="flex items-center gap-3">
          <Logo />
          <span>© {new Date().getFullYear()}</span>
        </div>
        <nav className="flex flex-wrap gap-x-5 gap-y-2" aria-label="Footer">
          {links.map((l) => (
            <Link key={l.to} to={l.to} className="hover:text-ink">
              {l.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
