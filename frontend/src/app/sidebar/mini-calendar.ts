import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';

import { CalendarStore } from '../core/calendar-store';
import { addMonths, dayKey, isSameDay, monthGrid } from '../core/date-utils';
import { Icon } from '../core/icon';

@Component({
  selector: 'app-mini-calendar',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <span class="label">{{ monthLabel() }}</span>
      <button class="icon-btn sm" type="button" aria-label="Previous month" (click)="viewDateChange.emit(shift(-1))">
        <app-icon name="chevronLeft" />
      </button>
      <button class="icon-btn sm" type="button" aria-label="Next month" (click)="viewDateChange.emit(shift(1))">
        <app-icon name="chevronRight" />
      </button>
    </div>
    <div class="grid" role="grid">
      @for (d of initials; track $index) {
        <span class="initial">{{ d }}</span>
      }
      @for (day of days(); track day.key) {
        <button
          type="button"
          class="day"
          [class.outside]="!day.inMonth"
          [class.today]="day.isToday"
          [class.has-items]="day.hasItems"
          [attr.aria-label]="day.label"
          (click)="daySelect.emit(day.date)"
        >
          {{ day.date.getDate() }}
        </button>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .head { display: flex; align-items: center; gap: 2px; padding: 0 4px 6px 8px; }
    .label { flex: 1; font-weight: 600; font-size: 13px; }
    .icon-btn.sm { width: 28px; height: 28px; }
    .icon-btn.sm app-icon { width: 16px; height: 16px; }
    .grid { display: grid; grid-template-columns: repeat(7, 1fr); row-gap: 2px; text-align: center; }
    .initial { font-size: 10px; font-weight: 600; color: var(--text-3); padding-bottom: 2px; }
    .day {
      position: relative; justify-self: center; width: 28px; height: 28px; padding: 0;
      border: none; border-radius: 50%; background: transparent; font-size: 11.5px; color: var(--text);
    }
    .day:hover { background: var(--surface-hover); }
    .day.outside { color: var(--text-3); }
    .day.today { background: var(--accent); color: #fff; font-weight: 600; }
    .day.has-items:not(.today)::after {
      content: ''; position: absolute; left: 50%; bottom: 3px; width: 3px; height: 3px;
      margin-left: -1.5px; border-radius: 50%; background: var(--accent);
    }
  `,
})
export class MiniCalendar {
  private readonly store = inject(CalendarStore);

  readonly viewDate = input.required<Date>();
  readonly viewDateChange = output<Date>();
  readonly daySelect = output<Date>();

  protected readonly initials = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  protected readonly monthLabel = computed(() =>
    this.viewDate().toLocaleDateString([], { month: 'long', year: 'numeric' }),
  );

  protected readonly days = computed(() => {
    const view = this.viewDate();
    const today = new Date();
    const busyDays = this.store.busyDays();
    return monthGrid(view)
      .flat()
      .map((date) => {
        const key = dayKey(date);
        return {
          date,
          key,
          inMonth: date.getMonth() === view.getMonth(),
          isToday: isSameDay(date, today),
          hasItems: busyDays.has(key),
          label: date.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' }),
        };
      });
  });

  protected shift(months: number): Date {
    return addMonths(this.viewDate(), months);
  }
}
