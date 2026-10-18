import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';

import { CalendarStore } from '../core/calendar-store';
import { addDaysToKey, dayKey, daysBetween, formatTime, isSameDay, monthGrid } from '../core/date-utils';
import { Icon } from '../core/icon';
import { CalendarEvent, Task } from '../core/models';
import { Segment, eventRange, layoutWeek } from './event-layout';

/** Rows of items a day cell shows before "N more" (bars + chips). */
const MAX_VISIBLE_ITEMS = 3;

/** What is being dragged. Kept in a signal because dataTransfer is unreadable during dragover. */
interface DragItem {
  kind: 'task' | 'event';
  id: number;
  /** For bars: which day of the event was grabbed, so that day lands on the drop target. */
  grabOffset: number;
}

interface DayCell {
  date: Date;
  key: string;
  inMonth: boolean;
  isToday: boolean;
  /** All-day / multi-day events covering this day (shown in full in the overflow list). */
  spanning: CalendarEvent[];
  events: CalendarEvent[];
  tasks: Task[];
  /** How many single-day chips fit under the week's bars. */
  chipBudget: number;
  hidden: number;
}

interface WeekRow {
  key: string;
  days: DayCell[];
  /** Bars drawn in this week (only the visible lanes). */
  segments: Segment[];
  visibleLanes: number;
}

@Component({
  selector: 'app-month-view',
  imports: [Icon, NgTemplateOutlet],
  templateUrl: './month-view.html',
  styleUrl: './month-view.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonthView {
  protected readonly store = inject(CalendarStore);

  readonly viewDate = input.required<Date>();
  readonly dayClick = output<string>();
  readonly taskClick = output<number>();
  readonly eventClick = output<number>();

  protected readonly weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  /** Day whose "+N more" overflow list is open. */
  protected readonly expandedDay = signal<string | null>(null);
  protected readonly formatTime = formatTime;
  protected readonly dragging = signal<DragItem | null>(null);
  protected readonly dropTarget = signal<string | null>(null);
  /** Read by screen readers after a drop. */
  protected readonly announcement = signal('');

  protected readonly weeks = computed<WeekRow[]>(() => {
    const view = this.viewDate();
    const today = new Date();
    const tasksByDay = this.store.tasksByDay();
    const eventsByDay = this.store.eventsByDay();
    const spanning = this.store.spanningEvents();

    return monthGrid(view).map((week) => {
      const keys = week.map(dayKey);
      const segments = layoutWeek(keys, spanning);
      const totalLanes = segments.reduce((max, s) => Math.max(max, s.lane + 1), 0);
      const visibleLanes = Math.min(totalLanes, MAX_VISIBLE_ITEMS);
      const chipBudget = MAX_VISIBLE_ITEMS - visibleLanes;

      const days = week.map((date, col) => {
        const key = keys[col];
        const covering = segments.filter((s) => s.startCol <= col && col <= s.endCol);
        const hiddenBars = covering.filter((s) => s.lane >= visibleLanes).length;
        const events = eventsByDay.get(key) ?? [];
        const tasks = tasksByDay.get(key) ?? [];
        return {
          date,
          key,
          inMonth: date.getMonth() === view.getMonth(),
          isToday: isSameDay(date, today),
          spanning: covering.map((s) => s.event),
          events,
          tasks,
          chipBudget,
          hidden: hiddenBars + Math.max(0, events.length + tasks.length - chipBudget),
        };
      });
      return { key: keys[0], days, segments: segments.filter((s) => s.lane < visibleLanes), visibleLanes };
    });
  });

  /** Events first (they are time-bound), then tasks, within the space left under the bars. */
  protected visibleEvents(cell: DayCell, all = false): CalendarEvent[] {
    return all ? cell.events : cell.events.slice(0, cell.chipBudget);
  }

  protected visibleTasks(cell: DayCell, all = false): Task[] {
    return all ? cell.tasks : cell.tasks.slice(0, Math.max(0, cell.chipBudget - cell.events.length));
  }

  /** Label on a bar: the start time appears only where the event actually starts. */
  protected barLabel(s: Segment): string {
    const time = !s.event.allDay && !s.continuesBefore ? `${formatTime(s.event.startAt)} ` : '';
    return time + s.event.title;
  }

  protected spanLabel(event: CalendarEvent): string {
    if (event.allDay) {
      return 'All day';
    }
    return `${formatTime(event.startAt)}–${formatTime(event.endAt)}`;
  }

  protected openDay(key: string): void {
    this.expandedDay.set(null);
    this.dayClick.emit(key);
  }

  protected toggleOverflow(key: string, event: Event): void {
    event.stopPropagation();
    this.expandedDay.update((current) => (current === key ? null : key));
  }

  protected openTask(id: number, event: Event): void {
    event.stopPropagation();
    this.expandedDay.set(null);
    this.taskClick.emit(id);
  }

  protected openEvent(id: number, event: Event): void {
    event.stopPropagation();
    this.expandedDay.set(null);
    this.eventClick.emit(id);
  }

  protected toggleTask(task: Task, event: Event): void {
    event.stopPropagation();
    this.store.toggleComplete(task);
  }

  // ---- Drag and drop rescheduling ----

  protected isDragging(kind: DragItem['kind'], id: number): boolean {
    const item = this.dragging();
    return item?.kind === kind && item.id === id;
  }

  protected onDragStart(event: DragEvent, kind: DragItem['kind'], id: number, grabOffset = 0): void {
    event.stopPropagation();
    this.dragging.set({ kind, id, grabOffset });
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      // Some browsers only start a drag when data is set.
      event.dataTransfer.setData('text/plain', `${kind}:${id}`);
    }
  }

  /** Works out which day of a multi-day bar was grabbed from the pointer position. */
  protected onBarDragStart(event: DragEvent, segment: Segment, week: WeekRow): void {
    const bar = event.currentTarget as HTMLElement;
    const rect = bar.getBoundingClientRect();
    const columns = segment.endCol - segment.startCol + 1;
    const col = Math.min(columns - 1, Math.max(0, Math.floor(((event.clientX - rect.left) / rect.width) * columns)));
    const grabbedDay = week.days[segment.startCol + col].key;
    const grabOffset = daysBetween(eventRange(segment.event).first, grabbedDay);
    this.onDragStart(event, 'event', segment.event.id, grabOffset);
  }

  protected onDragOver(event: DragEvent, key: string): void {
    if (!this.dragging()) {
      return; // not one of our chips (e.g. a file dragged from the desktop)
    }
    event.preventDefault(); // allows dropping here
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
    this.dropTarget.set(key);
  }

  protected onDragLeave(event: DragEvent, key: string): void {
    // Moving between children of the same cell fires dragleave too; ignore those.
    const cell = event.currentTarget as HTMLElement;
    if (!cell.contains(event.relatedTarget as Node | null) && this.dropTarget() === key) {
      this.dropTarget.set(null);
    }
  }

  protected onDrop(event: DragEvent, cell: DayCell): void {
    event.preventDefault();
    const item = this.dragging();
    this.onDragEnd();
    if (!item) {
      return;
    }
    if (item.kind === 'task') {
      const task = this.store.taskById().get(item.id);
      if (task) {
        this.store.moveTask(task, cell.key);
        this.announcement.set(`Moved ${task.title} to ${this.dayLabel(cell)}.`);
      }
    } else {
      const ev = this.store.events().find((e) => e.id === item.id);
      if (ev) {
        const newStart = addDaysToKey(cell.key, -item.grabOffset);
        this.store.moveEvent(ev, newStart);
        this.announcement.set(`Moved ${ev.title} to start ${this.dayLabel({ date: new Date(newStart + 'T00:00') })}.`);
      }
    }
  }

  protected onDragEnd(): void {
    this.dragging.set(null);
    this.dropTarget.set(null);
    this.expandedDay.set(null);
  }

  protected dayLabel(cell: { date: Date }): string {
    return cell.date.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
  }
}
