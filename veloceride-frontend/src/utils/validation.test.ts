import { describe, expect, it } from 'vitest';
import { registerSchema, rideSchema, vehicleSchema, reviewSchema, PASSWORD_REGEX, PHONE_REGEX } from '@/utils/validation';

describe('validation mirrors backend rules', () => {
  it('phone: optional +, 8–15 digits, no leading zero', () => {
    expect(PHONE_REGEX.test('+919876543210')).toBe(true);
    expect(PHONE_REGEX.test('98765432')).toBe(true);
    expect(PHONE_REGEX.test('0987654321')).toBe(false);
    expect(PHONE_REGEX.test('+91 98765')).toBe(false);
  });

  it('password: 8–64 with upper, lower, digit and symbol', () => {
    expect(PASSWORD_REGEX.test('Secret@123')).toBe(true);
    expect(PASSWORD_REGEX.test('secret@123')).toBe(false);
    expect(PASSWORD_REGEX.test('Secret123')).toBe(false);
    expect(PASSWORD_REGEX.test('S@1a')).toBe(false);
  });

  it('register requires matching passwords and reports it on confirmPassword', () => {
    const result = registerSchema.safeParse({
      fullName: 'Asha Verma',
      email: 'asha@example.com',
      phoneNumber: '+919876543210',
      password: 'Secret@123',
      confirmPassword: 'Secret@124',
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0].path).toEqual(['confirmPassword']);
  });

  it('vehicle number allows letters, digits, spaces and dashes (4–20)', () => {
    const base = { vehicleName: 'Car', vehicleType: 'SEDAN', brand: 'B', model: 'M', color: 'C', seatingCapacity: 4, registrationDocumentUrl: '' };
    expect(vehicleSchema.safeParse({ ...base, vehicleNumber: 'DL 01-AB 1234' }).success).toBe(true);
    expect(vehicleSchema.safeParse({ ...base, vehicleNumber: 'DL#1' }).success).toBe(false);
    expect(vehicleSchema.safeParse({ ...base, vehicleNumber: 'DL01', seatingCapacity: 21 }).success).toBe(false);
    expect(vehicleSchema.safeParse({ ...base, vehicleNumber: 'DL01', registrationDocumentUrl: 'ftp://x' }).success).toBe(false);
  });

  it('ride: source ≠ destination, arrival after departure, price ≤ 2 decimals', () => {
    const base = {
      vehicleId: 'v',
      source: 'Delhi',
      destination: 'Jaipur',
      departureDate: '2099-01-01',
      departureTime: '09:30',
      estimatedArrivalTime: '',
      totalSeats: 3,
      pricePerSeat: 650,
      description: '',
    };
    expect(rideSchema.safeParse(base).success).toBe(true);
    expect(rideSchema.safeParse({ ...base, destination: 'delhi ' }).success).toBe(false);
    expect(rideSchema.safeParse({ ...base, estimatedArrivalTime: '2099-01-01T09:00' }).success).toBe(false);
    expect(rideSchema.safeParse({ ...base, pricePerSeat: 10.555 }).success).toBe(false);
    expect(rideSchema.safeParse({ ...base, pricePerSeat: -1 }).success).toBe(false);
  });

  it('review rating must be 1–5', () => {
    expect(reviewSchema.safeParse({ rating: 0, comment: '' }).success).toBe(false);
    expect(reviewSchema.safeParse({ rating: 5, comment: '' }).success).toBe(true);
  });
});
