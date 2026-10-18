import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';

import { CalendarStore } from '../core/calendar-store';
import { formatDueDate } from '../core/date-utils';
import { Icon } from '../core/icon';
import { Task } from '../core/models';
import { MiniCalendar } from './mini-calendar';

@Component({
  selector: 'app-sidebar',
  imports: [Icon, MiniCalendar],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Sidebar {
  protected readonly store = inject(CalendarStore);

  readonly viewDate = input.required<Date>();
  readonly viewDateChange = output<Date>();
  readonly createTask = output<void>();
  readonly createEvent = output<void>();
  readonly openTask = output<number>();
  readonly resetDemo = output<void>();

  protected readonly formatDueDate = formatDueDate;

  /** Top-level tasks that still need work, soonest first; undated tasks go last. */
  protected readonly openTasks = computed(() =>
    (this.store.childrenOf().get(null) ?? [])
      .filter((t) => t.status !== 'DONE')
      .sort((a, b) => (a.dueAt ?? '9999').localeCompare(b.dueAt ?? '9999')),
  );

  protected readonly completedCount = computed(
    () => (this.store.childrenOf().get(null) ?? []).filter((t) => t.status === 'DONE').length,
  );

  protected isOverdue(task: Task): boolean {
    return task.dueAt != null && new Date(task.dueAt) < new Date();
  }
}
