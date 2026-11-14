import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import type { AuthResponse, Booking, PageResponse, Ride, UserProfile, Vehicle } from '@/types/dto';

/** MSW is used only in tests to stand in for the Spring backend; the app never ships mock data. */
export const API = 'http://localhost:8080/api';

export const server = setupServer();
export { http, HttpResponse };

export const ok = <T,>(data: T, status = 200) =>
  HttpResponse.json({ success: true, message: 'OK', data, timestamp: '2026-11-13T10:00:00' }, { status });

export const fail = (status: number, message: string, errors: { field: string; message: string }[] = []) =>
  HttpResponse.json({ success: false, message, errors, timestamp: '2026-11-13T10:00:00' }, { status });

export const page = <T,>(content: T[]): PageResponse<T> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  first: true,
  last: true,
});

export const user: UserProfile = {
  id: 'u-1',
  fullName: 'Asha Verma',
  email: 'asha@example.com',
  phoneNumber: '+919876543210',
  activeMode: 'PASSENGER',
  accountStatus: 'ACTIVE',
  role: 'USER',
  averageRating: 4.5,
  totalReviews: 2,
  emailVerified: true,
  phoneVerified: true,
  googleLinked: false,
  passwordSet: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

export const auth: AuthResponse = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresIn: 900,
  user,
};

export const ride: Ride = {
  id: 'r-1',
  source: 'Delhi',
  destination: 'Jaipur',
  departureDate: '2099-11-15',
  departureTime: '09:30:00',
  totalSeats: 3,
  availableSeats: 2,
  bookedSeats: 1,
  pricePerSeat: 650,
  rideStatus: 'SCHEDULED',
  driver: { id: 'd-1', fullName: 'Ravi Kumar', averageRating: 4.8, totalReviews: 23 },
  vehicle: { id: 'v-1', vehicleName: 'My Swift', vehicleType: 'HATCHBACK', brand: 'Maruti', model: 'Swift', color: 'White', isVerified: true },
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
};

export const booking: Booking = {
  id: 'b-1',
  bookingReference: 'BK-7KQ2M9XA',
  bookingStatus: 'PENDING',
  numberOfSeats: 2,
  totalAmount: 1300,
  ride: { id: 'r-1', source: 'Delhi', destination: 'Jaipur', departureDate: '2099-11-15', departureTime: '09:30:00', rideStatus: 'SCHEDULED', pricePerSeat: 650 },
  passenger: { id: 'p-1', fullName: 'Neha Singh' },
  driver: { id: 'u-1', fullName: 'Asha Verma' },
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
};

export const vehicle: Vehicle = {
  id: 'v-1',
  ownerId: 'u-1',
  vehicleName: 'My Swift',
  vehicleNumber: 'DL01AB1234',
  vehicleType: 'HATCHBACK',
  brand: 'Maruti',
  model: 'Swift',
  color: 'White',
  seatingCapacity: 4,
  verificationStatus: 'VERIFIED',
  isVerified: true,
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
};
