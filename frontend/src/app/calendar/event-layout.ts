import { dayKey } from '../core/date-utils';
import { CalendarEvent } from '../core/models';

/** First and last local day an event covers ("YYYY-MM-DD"). */
export interface DayRange {
  first: string;
  last: string;
}

/**
 * An event ending exactly at local midnight (e.g. 10pm to 12am) belongs to the day
 * before; otherwise a 2-hour evening event would look like it spans two days.
 */
export function eventRange(event: Pick<CalendarEvent, 'startAt' | 'endAt'>): DayRange {
  const start = new Date(event.startAt);
  let end = new Date(event.endAt);
  if (end > start && end.getHours() === 0 && end.getMinutes() === 0) {
    end = new Date(end.getTime() - 60_000);
  }
  return { first: dayKey(start), last: dayKey(end < start ? start : end) };
}

/** All-day and multi-day events are drawn as bars; others are single-day chips. */
export function isSpanning(event: CalendarEvent): boolean {
  if (event.allDay) {
    return true;
  }
  const range = eventRange(event);
  return range.first !== range.last;
}

/** One week row's piece of a spanning event. Columns are 0 (Sunday) to 6. */
export interface Segment {
  event: CalendarEvent;
  startCol: number;
  endCol: number;
  lane: number;
  /** The event started in an earlier week / ends in a later week (drawn with square ends). */
  continuesBefore: boolean;
  continuesAfter: boolean;
}

/**
 * Places spanning events into lanes for one week so each bar stays on the same row
 * across the days it covers. Earlier and longer events get the top lanes, like most
 * calendar apps.
 */
export function layoutWeek(weekDays: string[], events: CalendarEvent[]): Segment[] {
  const weekFirst = weekDays[0];
  const weekLast = weekDays[weekDays.length - 1];
  const pieces = events
    .map((event) => ({ event, range: eventRange(event) }))
    .filter(({ range }) => range.first <= weekLast && range.last >= weekFirst)
    .map(({ event, range }) => {
      const startCol = range.first < weekFirst ? 0 : weekDays.indexOf(range.first);
      const endCol = range.last > weekLast ? weekDays.length - 1 : weekDays.indexOf(range.last);
      return {
        event,
        startCol,
        endCol,
        continuesBefore: range.first < weekFirst,
        continuesAfter: range.last > weekLast,
      };
    })
    .sort(
      (a, b) =>
        a.startCol - b.startCol ||
        b.endCol - b.startCol - (a.endCol - a.startCol) ||
        a.event.startAt.localeCompare(b.event.startAt) ||
        a.event.id - b.event.id,
    );

  const laneEnds: number[] = []; // last occupied column per lane
  return pieces.map((piece) => {
    let lane = laneEnds.findIndex((end) => end < piece.startCol);
    if (lane === -1) {
      lane = laneEnds.length;
      laneEnds.push(piece.endCol);
    } else {
      laneEnds[lane] = piece.endCol;
    }
    return { ...piece, lane };
  });
}
