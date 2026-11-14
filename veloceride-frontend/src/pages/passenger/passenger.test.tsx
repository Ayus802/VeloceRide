import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SearchRidesPage from '@/pages/passenger/SearchRidesPage';
import { BookingModal } from '@/components/bookings/BookingModal';
import { ReviewModal } from '@/components/modals/ReviewModal';
import { API, booking, fail, http, ok, page, ride, server } from '@/test/server';
import { renderRoute, signIn } from '@/test/render';

vi.mock('@/components/maps/MapView', () => ({ MapView: () => null, ridePoints: () => [] }));

describe('search rides', () => {
  it('sends source, destination and date to the backend and lists results', async () => {
    let url: URL | null = null;
    server.use(
      http.get(`${API}/rides/search`, ({ request }) => {
        url = new URL(request.url);
        return ok(page([ride, { ...ride, id: 'r-2', availableSeats: 1, driver: { ...ride.driver, fullName: 'Meera Das' } }]));
      }),
    );
    renderRoute(<SearchRidesPage />, { path: '/rides/search', initialEntry: '/rides/search?from=Delhi&to=Jaipur&date=2099-11-15&seats=2' });

    expect(await screen.findByText('Ravi Kumar')).toBeInTheDocument();
    expect(url!.searchParams.get('source')).toBe('Delhi');
    expect(url!.searchParams.get('destination')).toBe('Jaipur');
    expect(url!.searchParams.get('date')).toBe('2099-11-15');
    expect(url!.searchParams.get('sort')).toBe('departureTime,asc');
    // Seats are not a backend filter: rides with too few seats are flagged, not hidden.
    expect(screen.getByText('Only 1 seat left')).toBeInTheDocument();
    expect(screen.getByText('2 rides available', { exact: false })).toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    server.use(http.get(`${API}/rides/search`, () => ok(page([]))));
    renderRoute(<SearchRidesPage />, { path: '/rides/search', initialEntry: '/rides/search?from=A&to=B&date=2099-11-15' });
    expect(await screen.findByText('No rides found')).toBeInTheDocument();
  });

  it('does not call the API without a complete query', () => {
    renderRoute(<SearchRidesPage />, { path: '/rides/search' });
    expect(screen.getByText('Where are you heading?')).toBeInTheDocument();
  });
});

describe('booking flow', () => {
  it('requests the chosen number of seats and opens the booking', async () => {
    signIn();
    let body: unknown;
    server.use(
      http.post(`${API}/bookings`, async ({ request }) => {
        body = await request.json();
        return ok({ ...booking, numberOfSeats: 2 }, 201);
      }),
    );
    const { router } = renderRoute(<BookingModal ride={ride} open onClose={() => {}} />, {
      path: '/rides/r-1',
      extraRoutes: [{ path: '/bookings/:id', element: <p>Booking details</p> }],
    });
    const dialog = screen.getByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'More seats' }));
    // availableSeats is 2: cannot go above it
    expect(within(dialog).getByRole('button', { name: 'More seats' })).toBeDisabled();
    expect(within(dialog).getByText('₹1,300')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send request' }));

    expect(await screen.findByText('Booking details')).toBeInTheDocument();
    expect(body).toEqual({ rideId: 'r-1', numberOfSeats: 2 });
    expect(router.state.location.pathname).toBe('/bookings/b-1');
  });

  it('shows the backend reason when booking is refused', async () => {
    signIn();
    server.use(http.post(`${API}/bookings`, () => fail(409, 'You already have an active booking for this ride')));
    renderRoute(<BookingModal ride={ride} open onClose={() => {}} />, { path: '/rides/r-1' });
    await userEvent.click(screen.getByRole('button', { name: 'Send request' }));
    expect(await screen.findByText('You already have an active booking for this ride')).toBeInTheDocument();
  });
});

describe('review submission', () => {
  it('requires a rating, then posts the review', async () => {
    signIn();
    let body: unknown;
    server.use(
      http.post(`${API}/reviews`, async ({ request }) => {
        body = await request.json();
        return ok({ id: 'rv-1' }, 201);
      }),
    );
    const onClose = vi.fn();
    renderRoute(<ReviewModal open onClose={onClose} rideId="r-1" reviewee={{ id: 'd-1', fullName: 'Ravi Kumar' }} role="driver" />);
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(await screen.findByText('Choose a rating')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: '4 stars' }));
    await userEvent.type(screen.getByLabelText(/Comment/), 'Smooth drive');
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(await screen.findByText('Review submitted')).toBeInTheDocument();
    expect(onClose).toHaveBeenCalled();
    expect(body).toEqual({ rideId: 'r-1', reviewedUserId: 'd-1', rating: 4, comment: 'Smooth drive' });
  });

  it('surfaces duplicate-review errors from the backend', async () => {
    signIn();
    server.use(http.post(`${API}/reviews`, () => fail(409, 'You have already reviewed this user for this ride')));
    renderRoute(<ReviewModal open onClose={() => {}} rideId="r-1" reviewee={{ id: 'd-1', fullName: 'Ravi Kumar' }} role="driver" />);
    await userEvent.click(screen.getByRole('radio', { name: '5 stars' }));
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(await screen.findByText('You have already reviewed this user for this ride')).toBeInTheDocument();
  });
});
