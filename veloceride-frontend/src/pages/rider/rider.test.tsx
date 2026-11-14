import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RideForm } from '@/components/forms/RideForm';
import { RequestActions } from '@/components/bookings/RequestActions';
import VehicleFormPage from '@/pages/vehicles/VehicleFormPage';
import type { RideRequest } from '@/types/dto';
import { API, booking, fail, http, ok, server, vehicle } from '@/test/server';
import { renderRoute, signIn } from '@/test/render';
import { AppError } from '@/utils/errors';

vi.mock('@/components/maps/MapView', () => ({ MapView: () => null, ridePoints: () => [] }));

describe('ride publishing form', () => {
  const fill = async () => {
    await userEvent.type(screen.getByLabelText('Leaving from'), 'Delhi');
    await userEvent.type(screen.getByLabelText('Going to'), 'Jaipur');
    await userEvent.clear(screen.getByLabelText('Date'));
    await userEvent.type(screen.getByLabelText('Date'), '2099-11-15');
    await userEvent.type(screen.getByLabelText('Departure time'), '09:30');
    await userEvent.type(screen.getByLabelText('Price per seat (₹)'), '650');
  };

  it('builds a RideRequest from the form', async () => {
    const onSubmit = vi.fn<(b: RideRequest) => Promise<unknown>>().mockResolvedValue(undefined);
    renderRoute(<RideForm vehicles={[vehicle]} submitLabel="Publish ride" onSubmit={onSubmit} />);
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Publish ride' }));
    await vi.waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][0]).toEqual({
      vehicleId: 'v-1',
      source: 'Delhi',
      destination: 'Jaipur',
      sourceLatitude: undefined,
      sourceLongitude: undefined,
      destinationLatitude: undefined,
      destinationLongitude: undefined,
      departureDate: '2099-11-15',
      departureTime: '09:30',
      estimatedArrivalTime: undefined,
      totalSeats: 3,
      pricePerSeat: 650,
      description: undefined,
    });
  });

  it('prevents more seats than the vehicle has', async () => {
    const onSubmit = vi.fn();
    renderRoute(<RideForm vehicles={[{ ...vehicle, seatingCapacity: 2 }]} submitLabel="Publish ride" onSubmit={onSubmit} />);
    await fill();
    await userEvent.clear(screen.getByLabelText('Seats offered'));
    await userEvent.type(screen.getByLabelText('Seats offered'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Publish ride' }));
    expect(await screen.findByText('This vehicle has 2 passenger seats')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('disables unverified vehicles and shows backend errors', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new AppError(422, 'Vehicle must be verified by an admin before it can be used to publish rides'));
    renderRoute(
      <RideForm vehicles={[vehicle, { ...vehicle, id: 'v-2', vehicleName: 'New car', isVerified: false, verificationStatus: 'PENDING' }]} submitLabel="Publish ride" onSubmit={onSubmit} />,
    );
    expect(screen.getByRole('option', { name: /New car/ })).toBeDisabled();
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Publish ride' }));
    expect(await screen.findByText(/must be verified by an admin/)).toBeInTheDocument();
  });

  it('locks committed terms when the ride has bookings', () => {
    renderRoute(
      <RideForm
        vehicles={[vehicle]}
        lockedTerms
        submitLabel="Save"
        onSubmit={vi.fn()}
        defaultValues={{ source: 'Delhi', destination: 'Jaipur', departureDate: '2099-11-15', departureTime: '09:30', pricePerSeat: 650, vehicleId: 'v-1' }}
      />,
    );
    expect(screen.getByLabelText('Leaving from')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Price per seat (₹)')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Seats offered')).not.toHaveAttribute('readonly');
  });
});

describe('booking request handling', () => {
  it('accepts a request after confirmation', async () => {
    signIn();
    let accepted = false;
    server.use(
      http.patch(`${API}/bookings/b-1/accept`, () => {
        accepted = true;
        return ok({ ...booking, bookingStatus: 'ACCEPTED' });
      }),
    );
    renderRoute(<RequestActions booking={booking} />);
    await userEvent.click(screen.getByRole('button', { name: 'Accept' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(/2 seats will be reserved/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Accept' }));
    expect(await screen.findByText('Neha Singh is confirmed')).toBeInTheDocument();
    expect(accepted).toBe(true);
  });

  it('reports a backend refusal (e.g. not enough seats)', async () => {
    signIn();
    server.use(http.patch(`${API}/bookings/b-1/accept`, () => fail(422, 'Not enough seats available: requested 2, available 1')));
    renderRoute(<RequestActions booking={booking} />);
    await userEvent.click(screen.getByRole('button', { name: 'Accept' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Accept' }));
    expect(await screen.findByText(/Not enough seats available/)).toBeInTheDocument();
  });
});

describe('vehicle management', () => {
  it('creates a vehicle with only backend-supported fields', async () => {
    signIn();
    let body: Record<string, unknown> = {};
    server.use(
      http.post(`${API}/vehicles`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return ok({ ...vehicle, verificationStatus: 'PENDING', isVerified: false }, 201);
      }),
    );
    renderRoute(<VehicleFormPage />, { path: '/vehicles/new', extraRoutes: [{ path: '/vehicles', element: <p>Vehicles list</p> }] });
    await userEvent.type(screen.getByLabelText('Nickname'), 'My Swift');
    await userEvent.type(screen.getByLabelText('Brand'), 'Maruti');
    await userEvent.type(screen.getByLabelText('Model'), 'Swift');
    await userEvent.type(screen.getByLabelText('Colour'), 'White');
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'HATCHBACK');
    await userEvent.type(screen.getByLabelText('Registration number'), 'DL 01 AB 1234');
    await userEvent.click(screen.getByRole('button', { name: 'Add vehicle' }));

    expect(await screen.findByText('Vehicles list')).toBeInTheDocument();
    expect(body).toEqual({
      vehicleName: 'My Swift',
      vehicleNumber: 'DL 01 AB 1234',
      vehicleType: 'HATCHBACK',
      brand: 'Maruti',
      model: 'Swift',
      color: 'White',
      seatingCapacity: 4,
    });
  });

  it('shows duplicate registration numbers on the field', async () => {
    signIn();
    server.use(http.post(`${API}/vehicles`, () => fail(409, 'A vehicle with this number is already registered')));
    renderRoute(<VehicleFormPage />, { path: '/vehicles/new' });
    await userEvent.type(screen.getByLabelText('Nickname'), 'Car');
    await userEvent.type(screen.getByLabelText('Brand'), 'B');
    await userEvent.type(screen.getByLabelText('Model'), 'M');
    await userEvent.type(screen.getByLabelText('Colour'), 'C');
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'SEDAN');
    await userEvent.type(screen.getByLabelText('Registration number'), 'DL01AB1234');
    await userEvent.click(screen.getByRole('button', { name: 'Add vehicle' }));
    expect(await screen.findByText('A vehicle with this number is already registered')).toBeInTheDocument();
  });
});
