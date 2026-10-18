import { combineDateTime, dayKey, daysBetween, monthGrid, shiftDays, toOffsetIso } from './date-utils';

describe('date-utils', () => {
  it('builds a grid of whole weeks that starts on Sunday and covers the month', () => {
    // October 2026 starts on a Thursday and ends on a Saturday.
    const weeks = monthGrid(new Date(2026, 9, 1));

    expect(weeks.length).toBe(5);
    expect(weeks.every((w) => w.length === 7)).toBe(true);
    expect(dayKey(weeks[0][0])).toBe('2026-09-27');
    expect(weeks[0][0].getDay()).toBe(0);
    expect(dayKey(weeks[4][6])).toBe('2026-10-31');
  });

  it('uses six rows when the month spills into a sixth week', () => {
    // August 2026 starts on a Saturday.
    expect(monthGrid(new Date(2026, 7, 1)).length).toBe(6);
  });

  it('formats local date-times with their UTC offset', () => {
    const date = combineDateTime('2026-10-25', '23:59');
    const iso = toOffsetIso(date);

    expect(iso).toMatch(/^2026-10-25T23:59:00[+-]\d{2}:\d{2}$/);
    expect(new Date(iso).getTime()).toBe(date.getTime());
  });

  it('counts calendar days between dates, including across DST and month ends', () => {
    expect(daysBetween('2026-10-18', '2026-10-22')).toBe(4);
    expect(daysBetween('2026-10-30', '2026-11-02')).toBe(3); // US DST ends Nov 1
    expect(daysBetween('2026-10-22', '2026-10-18')).toBe(-4);
  });

  it('shifts by whole days and keeps the local time of day across DST', () => {
    const start = combineDateTime('2026-10-31', '09:00');

    const moved = shiftDays(start.toISOString(), 2); // lands after the DST change

    expect(dayKey(moved)).toBe('2026-11-02');
    expect(moved.getHours()).toBe(9);
    expect(moved.getMinutes()).toBe(0);
  });
});
