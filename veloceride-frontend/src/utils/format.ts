import { PLATFORM_TIME_ZONE } from '@/constants/config';
import type { Instant, LocalDate, LocalDateTime, LocalTime } from '@/types/dto';

const moneyFmt = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export function formatMoney(value: number | null | undefined): string {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return moneyFmt.format(Number(value));
}

/**
 * Ride dates/times are already in the platform time zone. They are parsed as plain calendar values
 * (no Date/time-zone conversion) so a browser in another zone still shows what the driver entered.
 */
function parseLocalDate(date: LocalDate): { y: number; m: number; d: number } {
  const [y, m, d] = date.split('-').map(Number);
  return { y, m, d };
}

const WEEKDAY = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function formatDate(date: LocalDate | null | undefined, opts: { weekday?: boolean; year?: boolean } = {}): string {
  if (!date) return '—';
  const { y, m, d } = parseLocalDate(date);
  if (!y || !m || !d) return date;
  // UTC constructor only to derive the weekday of this calendar date; no zone shift involved.
  const weekday = WEEKDAY[new Date(Date.UTC(y, m - 1, d)).getUTCDay()];
  const base = `${d} ${MONTH[m - 1]}`;
  const withYear = opts.year ? `${base} ${y}` : base;
  return opts.weekday === false ? withYear : `${weekday}, ${withYear}`;
}

export function formatTime(time: LocalTime | string | null | undefined): string {
  if (!time) return '—';
  return time.slice(0, 5);
}

/** "2026-11-15T14:30:00" → "Sat, 15 Nov · 14:30" */
export function formatLocalDateTime(value: LocalDateTime | null | undefined): string {
  if (!value) return '—';
  const [date, time] = value.split('T');
  return `${formatDate(date)} · ${formatTime(time)}`;
}

const instantFmt = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
const instantDateFmt = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' });

export function formatInstant(value: Instant | null | undefined, dateOnly = false): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return (dateOnly ? instantDateFmt : instantFmt).format(d);
}

export function timeAgo(value: Instant | null | undefined, now = Date.now()): string {
  if (!value) return '—';
  const diff = Math.round((now - new Date(value).getTime()) / 1000);
  if (diff < 10) return 'just now';
  if (diff < 60) return `${diff}s ago`;
  const mins = Math.floor(diff / 60);
  if (mins < 60) return `${mins} min ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} d ago`;
  return formatInstant(value, true);
}

/** Today's date (YYYY-MM-DD) in the platform time zone — used for date pickers and search validation. */
export function todayInPlatformZone(now = new Date()): LocalDate {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: PLATFORM_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}

/** Sortable key for a ride's departure (string compare works for ISO-like values). */
export function departureKey(date: LocalDate, time: LocalTime): string {
  return `${date}T${time.length === 5 ? `${time}:00` : time}`;
}

export function formatRating(value: number | null | undefined): string {
  if (value == null) return 'New';
  return value.toFixed(1);
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export function plural(n: number, one: string, many = `${one}s`): string {
  return `${n} ${n === 1 ? one : many}`;
}
