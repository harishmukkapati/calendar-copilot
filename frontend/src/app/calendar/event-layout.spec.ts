import { combineDateTime } from '../core/date-utils';
import { CalendarEvent } from '../core/models';
import { eventRange, isSpanning, layoutWeek } from './event-layout';

let nextId = 1;
function ev(start: [string, string], end: [string, string], allDay = false): CalendarEvent {
  return {
    id: nextId++,
    title: `E${nextId}`,
    description: null,
    color: 'blue',
    allDay,
    startAt: combineDateTime(...start).toISOString(),
    endAt: combineDateTime(...end).toISOString(),
    createdAt: '',
    updatedAt: '',
  };
}

// Week of Sun Oct 18 to Sat Oct 24, 2026
const WEEK = ['2026-10-18', '2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22', '2026-10-23', '2026-10-24'];

describe('event-layout', () => {
  it('treats an event ending at midnight as ending the previous day', () => {
    const evening = ev(['2026-10-20', '22:00'], ['2026-10-21', '00:00']);

    expect(eventRange(evening)).toEqual({ first: '2026-10-20', last: '2026-10-20' });
    expect(isSpanning(evening)).toBe(false);
  });

  it('draws all-day and multi-day events as bars, single-day timed events as chips', () => {
    expect(isSpanning(ev(['2026-10-20', '09:00'], ['2026-10-20', '10:00']))).toBe(false);
    expect(isSpanning(ev(['2026-10-20', '00:00'], ['2026-10-20', '23:59'], true))).toBe(true);
    expect(isSpanning(ev(['2026-10-20', '18:00'], ['2026-10-22', '14:00']))).toBe(true);
  });

  it('keeps each bar in one lane and packs non-overlapping bars into the same lane', () => {
    const trip = ev(['2026-10-19', '00:00'], ['2026-10-21', '23:59'], true); // Mon to Wed
    const conf = ev(['2026-10-20', '09:00'], ['2026-10-23', '17:00']); // Tue to Fri, overlaps trip
    const later = ev(['2026-10-22', '00:00'], ['2026-10-24', '23:59'], true); // Thu to Sat, overlaps conf only

    const segments = layoutWeek(WEEK, [later, conf, trip]);
    const byId = new Map(segments.map((s) => [s.event.id, s]));

    expect(byId.get(trip.id)).toMatchObject({ startCol: 1, endCol: 3, lane: 0 });
    expect(byId.get(conf.id)).toMatchObject({ startCol: 2, endCol: 5, lane: 1 });
    expect(byId.get(later.id)).toMatchObject({ startCol: 4, endCol: 6, lane: 0 }); // reuses lane 0 after the trip
  });

  it('clips bars that cross week boundaries and marks the continuation', () => {
    const longBreak = ev(['2026-10-15', '00:00'], ['2026-10-27', '23:59'], true);

    const [segment] = layoutWeek(WEEK, [longBreak]);

    expect(segment).toMatchObject({ startCol: 0, endCol: 6, continuesBefore: true, continuesAfter: true });
    expect(layoutWeek(WEEK, [ev(['2026-10-05', '00:00'], ['2026-10-06', '23:59'], true)])).toEqual([]);
  });
});
