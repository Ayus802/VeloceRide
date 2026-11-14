import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HomePage from '@/pages/public/HomePage';
import DashboardPage from '@/pages/dashboard/DashboardPage';
import { API, booking, http, ok, page, ride, server } from '@/test/server';
import { renderRoute, signIn } from '@/test/render';
import type { AppNotification } from '@/types/dto';

vi.mock('@/components/maps/MapView', () => ({ MapView: () => null, ridePoints: () => [] }));

describe('public landing page', () => {
  it('shows the landing page to visitors with working search', async () => {
    server.use(http.get(`${API}/rides`, () => ok(page([]))));
    const { router } = renderRoute(<HomePage />, { path: '/' });

    expect(screen.getByRole('heading', { level: 1, name: 'Your journey, shared.' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Publish a ride' })).toHaveAttribute('href', '/rides/new');
    expect(screen.getByRole('link', { name: 'Join VeloceRide' })).toHaveAttribute('href', '/register');

    await userEvent.type(screen.getByPlaceholderText('Leaving from'), 'Delhi');
    await userEvent.type(screen.getByPlaceholderText('Going to'), 'Jaipur');
    await userEvent.click(screen.getByRole('button', { name: 'Swap departure and destination' }));
    await userEvent.click(screen.getByRole('button', { name: 'Search rides' }));

    expect(router.state.location.pathname).toBe('/rides/search');
    const params = new URLSearchParams(router.state.location.search);
    expect(params.get('from')).toBe('Jaipur');
    expect(params.get('to')).toBe('Delhi');
    expect(params.get('seats')).toBe('1');
  });

  it('lists real upcoming rides and hides the section when there are none', async () => {
    server.use(http.get(`${API}/rides`, () => ok(page([ride]))));
    renderRoute(<HomePage />, { path: '/' });
    expect(await screen.findByRole('heading', { name: 'Rides leaving soon' })).toBeInTheDocument();
    expect(screen.getByText('Ravi Kumar')).toBeInTheDocument();
  });

  it('sends signed-in users to their dashboard', async () => {
    signIn();
    renderRoute(<HomePage />, { path: '/', extraRoutes: [{ path: '/dashboard', element: <p>Dashboard</p> }] });
    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
  });
});

function dashboardApi({ bookings = [] as typeof booking[], notifications = [] as AppNotification[], anyRides = 0 } = {}) {
  server.use(
    http.get(`${API}/bookings/my-bookings`, ({ request }) =>
      ok(page(bookings.filter((b) => b.bookingStatus === new URL(request.url).searchParams.get('status')))),
    ),
    http.get(`${API}/rides/my-rides`, ({ request }) => {
      const status = new URL(request.url).searchParams.get('status');
      return ok(status ? page([]) : { ...page([]), totalElements: anyRides });
    }),
    http.get(`${API}/bookings/received-requests`, () => ok(page([]))),
    http.get(`${API}/notifications`, () => ok(page(notifications))),
  );
}

describe('dashboard', () => {
  it('shows polished empty states without duplicate actions', async () => {
    signIn();
    dashboardApi();
    renderRoute(<DashboardPage />, { path: '/dashboard' });

    expect(await screen.findByText('No upcoming trips')).toBeInTheDocument();
    expect(await screen.findByText('You haven’t published a ride yet.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /Find a ride/ })).toHaveLength(1);
    expect(screen.getAllByRole('link', { name: /^Publish a ride/ })).toHaveLength(1);
    expect(screen.getByRole('link', { name: /Explore rides/ })).toHaveAttribute('href', '/rides/search');
    expect(screen.getByRole('link', { name: /Publish your first ride/ })).toHaveAttribute('href', '/rides/new');
    expect(screen.queryByText('Recent activity')).not.toBeInTheDocument();
  });

  it('uses real data instead of empty states when it exists', async () => {
    signIn();
    dashboardApi({
      bookings: [{ ...booking, bookingStatus: 'ACCEPTED', passenger: { id: 'u-1', fullName: 'Asha Verma' }, driver: { id: 'd-1', fullName: 'Ravi Kumar' } }],
      anyRides: 2,
      notifications: [
        { id: 'n-1', title: 'Booking accepted', message: 'Your request was accepted', notificationType: 'BOOKING_ACCEPTED', referenceId: 'b-1', isRead: false, createdAt: new Date().toISOString() },
      ],
    });
    renderRoute(<DashboardPage />, { path: '/dashboard' });

    expect(await screen.findByText(/with Ravi Kumar/)).toBeInTheDocument();
    expect(screen.queryByText('No upcoming trips')).not.toBeInTheDocument();
    expect(await screen.findByText('No upcoming rides to drive')).toBeInTheDocument();
    expect(await screen.findByText('Recent activity')).toBeInTheDocument();
    expect(screen.getByText('Booking accepted')).toBeInTheDocument();
  });
});
