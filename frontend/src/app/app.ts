import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';

import { MonthView } from './calendar/month-view';
import { CalendarStore } from './core/calendar-store';
import { addMonths, dayKey, startOfMonth } from './core/date-utils';
import { Icon } from './core/icon';
import { EventModal } from './event-modal/event-modal';
import { Sidebar } from './sidebar/sidebar';
import { TaskModal } from './task-modal/task-modal';

export type ModalState =
  | { kind: 'task-create'; date: string | null }
  | { kind: 'task-edit'; taskId: number }
  | { kind: 'event-create'; date: string }
  | { kind: 'event-edit'; eventId: number };

@Component({
  selector: 'app-root',
  imports: [Sidebar, MonthView, TaskModal, EventModal, Icon],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly store = inject(CalendarStore);

  protected readonly viewDate = signal(startOfMonth(new Date()));
  protected readonly modal = signal<ModalState | null>(null);
  protected readonly sidebarOpen = signal(false);

  /** Wrapping the modal in a keyed list recreates it when switching between items. */
  protected readonly modals = computed(() => {
    const m = this.modal();
    return m ? [m] : [];
  });

  protected readonly monthLabel = computed(() =>
    this.viewDate().toLocaleDateString([], { month: 'long', year: 'numeric' }),
  );

  constructor() {
    this.store.load();

    // Auto-dismiss error toasts.
    effect((onCleanup) => {
      if (this.store.error()) {
        const timer = setTimeout(() => this.store.dismissError(), 5000);
        onCleanup(() => clearTimeout(timer));
      }
    });
  }

  protected modalKey(m: ModalState): string {
    switch (m.kind) {
      case 'task-edit':
        return `task-${m.taskId}`;
      case 'event-edit':
        return `event-${m.eventId}`;
      default:
        return m.kind;
    }
  }

  protected setViewDate(date: Date): void {
    this.viewDate.set(startOfMonth(date));
    this.sidebarOpen.set(false);
  }

  protected shiftMonth(months: number): void {
    this.viewDate.update((d) => addMonths(d, months));
  }

  protected goToToday(): void {
    this.viewDate.set(startOfMonth(new Date()));
  }

  protected createTask(date: string | null = dayKey(new Date())): void {
    this.sidebarOpen.set(false);
    this.modal.set({ kind: 'task-create', date });
  }

  protected createEvent(date: string = dayKey(new Date())): void {
    this.sidebarOpen.set(false);
    this.modal.set({ kind: 'event-create', date });
  }

  protected openTask(taskId: number): void {
    this.sidebarOpen.set(false);
    this.modal.set({ kind: 'task-edit', taskId });
  }

  protected openEvent(eventId: number): void {
    this.modal.set({ kind: 'event-edit', eventId });
  }

  protected closeModal(): void {
    this.modal.set(null);
  }

  protected resetDemo(): void {
    if (confirm('Replace your calendar with a fresh copy of the demo data?')) {
      this.sidebarOpen.set(false);
      this.store.resetDemo();
    }
  }
}
