/** Date helpers. All calendar math happens in the browser's local time zone. */

/** "YYYY-MM-DD" for a local date; used as the key for grouping items by day. */
export function dayKey(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function dayKeyOf(iso: string): string {
  return dayKey(new Date(iso));
}

export function timeValue(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Combines <input type="date"> and <input type="time"> values into a local Date. */
export function combineDateTime(day: string, time: string): Date {
  const [y, m, d] = day.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  return new Date(y, m - 1, d, hh, mm);
}

/** ISO-8601 with the local UTC offset, e.g. 2026-10-25T23:59:00-04:00 (what the API expects). */
export function toOffsetIso(date: Date): string {
  const offsetMinutes = -date.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? '+' : '-';
  const abs = Math.abs(offsetMinutes);
  return (
    `${dayKey(date)}T${timeValue(date)}:00` + `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  );
}

export function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

export function addMonths(date: Date, months: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + months, 1);
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

export function isSameDay(a: Date, b: Date): boolean {
  return dayKey(a) === dayKey(b);
}

/**
 * The weeks shown for a month: starts on the Sunday on or before the 1st and
 * ends on the Saturday on or after the last day (4-6 rows).
 */
export function monthGrid(viewDate: Date): Date[][] {
  const first = startOfMonth(viewDate);
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0);
  let cursor = addDays(first, -first.getDay());
  const weeks: Date[][] = [];
  while (cursor <= last || weeks.length === 0) {
    const week: Date[] = [];
    for (let i = 0; i < 7; i++) {
      week.push(cursor);
      cursor = addDays(cursor, 1);
    }
    weeks.push(week);
  }
  return weeks;
}

export function formatTime(iso: string): string {
  return new Date(iso)
    .toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
    .replace(':00', '')
    .replace(' ', '')
    .toLowerCase();
}

export function formatDueDate(iso: string): string {
  return new Date(iso).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** Whole calendar days from one "YYYY-MM-DD" to another (DST-safe: counts dates, not hours). */
export function daysBetween(fromDay: string, toDay: string): number {
  const toUtc = (day: string) => {
    const [y, m, d] = day.split('-').map(Number);
    return Date.UTC(y, m - 1, d);
  };
  return Math.round((toUtc(toDay) - toUtc(fromDay)) / 86_400_000);
}

/** Same local time of day, `days` calendar days later (keeps 9:00 at 9:00 across DST changes). */
export function shiftDays(iso: string, days: number): Date {
  const d = new Date(iso);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + days, d.getHours(), d.getMinutes(), d.getSeconds());
}
