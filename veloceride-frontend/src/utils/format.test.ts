import { describe, expect, it } from 'vitest';
import { departureKey, formatDate, formatTime, formatLocalDateTime, initials, timeAgo, todayInPlatformZone } from '@/utils/format';

describe('format', () => {
  it('formats platform-local dates without time-zone shifts', () => {
    expect(formatDate('2026-11-15')).toBe('Sun, 15 Nov');
    expect(formatDate('2026-11-15', { year: true, weekday: false })).toBe('15 Nov 2026');
  });

  it('trims seconds from times', () => {
    expect(formatTime('09:30:00')).toBe('09:30');
    expect(formatLocalDateTime('2026-11-15T14:30:00')).toBe('Sun, 15 Nov · 14:30');
  });

  it('builds sortable departure keys', () => {
    expect(departureKey('2026-11-15', '09:30') < departureKey('2026-11-15', '10:00:00')).toBe(true);
  });

  it('computes today in the platform zone (Asia/Kolkata)', () => {
    // 2026-11-14 20:00 UTC is already 15 Nov 01:30 in India.
    expect(todayInPlatformZone(new Date('2026-11-14T20:00:00Z'))).toBe('2026-11-15');
  });

  it('relative time and initials', () => {
    const now = new Date('2026-11-13T10:00:00Z').getTime();
    expect(timeAgo('2026-11-13T09:59:30Z', now)).toBe('30s ago');
    expect(timeAgo('2026-11-13T09:00:00Z', now)).toBe('1 h ago');
    expect(initials('Asha Rani Verma')).toBe('AV');
  });
});
