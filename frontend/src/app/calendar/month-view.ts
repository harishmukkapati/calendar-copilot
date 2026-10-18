import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

import { CalendarStore } from '../core/calendar-store';
import { dayKey, formatTime, isSameDay, monthGrid } from '../core/date-utils';
import { Icon } from '../core/icon';
import { CalendarEvent, Task } from '../core/models';

const MAX_VISIBLE_ITEMS = 3;

interface DayCell {
  date: Date;
  key: string;
  inMonth: boolean;
  isToday: boolean;
  events: CalendarEvent[];
  tasks: Task[];
  hidden: number;
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

  protected readonly weeks = computed<DayCell[][]>(() => {
    const view = this.viewDate();
    const today = new Date();
    const tasksByDay = this.store.tasksByDay();
    const eventsByDay = this.store.eventsByDay();
    return monthGrid(view).map((week) =>
      week.map((date) => {
        const key = dayKey(date);
        const events = eventsByDay.get(key) ?? [];
        const tasks = tasksByDay.get(key) ?? [];
        return {
          date,
          key,
          inMonth: date.getMonth() === view.getMonth(),
          isToday: isSameDay(date, today),
          events,
          tasks,
          hidden: Math.max(0, events.length + tasks.length - MAX_VISIBLE_ITEMS),
        };
      }),
    );
  });

  /** Events first (they are time-bound), then tasks, capped so cells keep a fixed height. */
  protected visibleEvents(cell: DayCell, all = false): CalendarEvent[] {
    return all ? cell.events : cell.events.slice(0, MAX_VISIBLE_ITEMS);
  }

  protected visibleTasks(cell: DayCell, all = false): Task[] {
    return all ? cell.tasks : cell.tasks.slice(0, Math.max(0, MAX_VISIBLE_ITEMS - cell.events.length));
  }

  protected parentTitle(task: Task): string | null {
    return task.parentId != null ? (this.store.taskById().get(task.parentId)?.title ?? null) : null;
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

  protected dayLabel(cell: DayCell): string {
    return cell.date.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
  }
}
