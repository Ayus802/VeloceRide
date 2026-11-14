import { z } from 'zod';
import { VEHICLE_TYPES } from '@/constants/labels';

/**
 * Zod schemas mirror the backend Bean Validation rules exactly (ValidationPatterns.java and the DTOs).
 * They are intentionally not stricter, so the server stays the final authority.
 */
export const PHONE_REGEX = /^\+?[1-9]\d{7,14}$/;
export const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,64}$/;
export const VEHICLE_NUMBER_REGEX = /^[A-Za-z0-9 -]{4,20}$/;
const URL_REGEX = /^https?:\/\/\S+$/;

export const PASSWORD_HINT = '8–64 characters with upper- and lower-case letters, a number and a symbol.';

const required = (label = 'This field') => z.string().trim().min(1, `${label} is required`);

const email = z.string().trim().min(1, 'Email is required').max(255).email('Enter a valid email address');
const phone = z.string().trim().regex(PHONE_REGEX, 'Enter 8–15 digits, optionally starting with +');
const password = z.string().regex(PASSWORD_REGEX, `Password must be ${PASSWORD_HINT.toLowerCase()}`);
const fullName = z.string().trim().min(2, 'Name must be at least 2 characters').max(100, 'Name is too long');
const optionalUrl = z
  .string()
  .trim()
  .max(1000, 'URL is too long')
  .refine((v) => v === '' || URL_REGEX.test(v), 'Enter a full http(s) URL');

const matchPasswords =
  <K extends string>(passwordKey: K) =>
  (data: Record<K | 'confirmPassword', string>, ctx: z.RefinementCtx) => {
    if (data.confirmPassword !== data[passwordKey]) {
      ctx.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Passwords do not match' });
    }
  };

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'Password is required'),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName,
    email,
    phoneNumber: phone,
    password,
    confirmPassword: z.string().min(1, 'Confirm your password'),
  })
  .superRefine(matchPasswords('password'));
export type RegisterValues = z.infer<typeof registerSchema>;

export const forgotPasswordSchema = z.object({ email });

export const resetPasswordSchema = z
  .object({ newPassword: password, confirmPassword: z.string().min(1, 'Confirm your password') })
  .superRefine(matchPasswords('newPassword'));
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: password,
    confirmPassword: z.string().min(1, 'Confirm your password'),
  })
  .superRefine(matchPasswords('newPassword'));
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

export const profileSchema = z.object({
  fullName,
  phoneNumber: phone,
  profilePicture: optionalUrl,
});
export type ProfileValues = z.infer<typeof profileSchema>;

const intInRange = (label: string, min: number, max: number) =>
  z
    .number({ error: `${label} is required` })
    .int(`${label} must be a whole number`)
    .min(min, `${label} must be at least ${min}`)
    .max(max, `${label} must be at most ${max}`);

export const vehicleSchema = z.object({
  vehicleName: required('Vehicle name').max(100),
  vehicleNumber: z
    .string()
    .trim()
    .regex(VEHICLE_NUMBER_REGEX, 'Use 4–20 letters, digits, spaces or dashes'),
  vehicleType: z.enum(VEHICLE_TYPES as [string, ...string[]], { error: 'Choose a vehicle type' }),
  brand: required('Brand').max(50),
  model: required('Model').max(50),
  color: required('Colour').max(30),
  seatingCapacity: intInRange('Passenger seats', 1, 20),
  registrationDocumentUrl: optionalUrl,
});
export type VehicleValues = z.infer<typeof vehicleSchema>;

const optionalCoord = (min: number, max: number) => z.number().min(min).max(max).optional();

export const rideSchema = z
  .object({
    vehicleId: z.string().min(1, 'Choose a vehicle'),
    source: required('Departure').max(200),
    destination: required('Destination').max(200),
    sourceLatitude: optionalCoord(-90, 90),
    sourceLongitude: optionalCoord(-180, 180),
    destinationLatitude: optionalCoord(-90, 90),
    destinationLongitude: optionalCoord(-180, 180),
    departureDate: z.string().min(1, 'Date is required'),
    departureTime: z.string().min(1, 'Time is required'),
    estimatedArrivalTime: z.string(),
    totalSeats: intInRange('Seats', 1, 20),
    pricePerSeat: z
      .number({ error: 'Price is required' })
      .min(0, 'Price cannot be negative')
      .max(99_999_999.99, 'Price is too high')
      .refine((v) => /^\d+(\.\d{1,2})?$/.test(String(v)), 'Use at most 2 decimals'),
    description: z.string().max(1000, 'Keep notes under 1000 characters'),
  })
  .superRefine((v, ctx) => {
    if (v.source.trim().toLowerCase() === v.destination.trim().toLowerCase() && v.source.trim()) {
      ctx.addIssue({ code: 'custom', path: ['destination'], message: 'Destination must differ from departure' });
    }
    if (v.estimatedArrivalTime && v.departureDate && v.departureTime) {
      const dep = `${v.departureDate}T${v.departureTime}`;
      if (v.estimatedArrivalTime <= dep) {
        ctx.addIssue({ code: 'custom', path: ['estimatedArrivalTime'], message: 'Arrival must be after departure' });
      }
    }
  });
export type RideValues = z.infer<typeof rideSchema>;

export const reviewSchema = z.object({
  rating: z.number({ error: 'Choose a rating' }).int().min(1, 'Choose a rating').max(5),
  comment: z.string().max(1000, 'Keep it under 1000 characters'),
});
export type ReviewValues = z.infer<typeof reviewSchema>;
