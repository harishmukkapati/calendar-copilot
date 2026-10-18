import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { CalendarStore } from '../core/calendar-store';
import { addDaysToKey, combineDateTime, dayKey, daysBetween, timeValue, toOffsetIso } from '../core/date-utils';
import { inputChecked, inputValue } from '../core/dom';
import { Icon } from '../core/icon';
import { ModalDialog } from '../core/modal-dialog';
import { CalendarEvent, EVENT_COLORS, EventColor } from '../core/models';

/** Matches the backend limit (EventService.MAX_LENGTH). */
const MAX_EVENT_DAYS = 62;

@Component({
  selector: 'app-event-modal',
  imports: [Icon, ModalDialog],
  templateUrl: './event-modal.html',
  styleUrl: './event-modal.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventModal implements OnInit {
  private readonly store = inject(CalendarStore);

  readonly eventId = input<number | null>(null);
  readonly initialDate = input<string | null>(null);
  readonly closed = output<void>();
  readonly switchToTask = output<string>();

  private readonly dialog = viewChild.required(ModalDialog);

  protected readonly inputValue = inputValue;
  protected readonly inputChecked = inputChecked;
  protected readonly colors = EVENT_COLORS;
  protected readonly title = signal('');
  protected readonly allDay = signal(false);
  protected readonly startDate = signal('');
  protected readonly startTime = signal('09:00');
  protected readonly endDate = signal('');
  protected readonly endTime = signal('10:00');
  protected readonly color = signal<EventColor>('blue');
  protected readonly description = signal('');
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly confirmingDelete = signal(false);

  protected readonly event = computed<CalendarEvent | undefined>(() =>
    this.store.events().find((e) => e.id === this.eventId()),
  );
  protected readonly isEdit = computed(() => this.eventId() != null);
  protected readonly titleError = computed(() => this.submitted() && !this.title().trim());

  private readonly start = computed(() =>
    this.startDate() ? combineDateTime(this.startDate(), this.allDay() ? '00:00' : this.startTime()) : null,
  );
  private readonly end = computed(() =>
    this.endDate() ? combineDateTime(this.endDate(), this.allDay() ? '23:59' : this.endTime()) : null,
  );
  /** Shown as soon as the range is wrong, not only after pressing Save. */
  protected readonly rangeError = computed(() => {
    const start = this.start();
    const end = this.end();
    if (!start || !end) {
      return this.submitted() ? 'Choose a start and end date.' : null;
    }
    if (end < start) {
      return this.allDay() ? 'The end date must be on or after the start date.' : 'The event must end after it starts.';
    }
    if (daysBetween(this.startDate(), this.endDate()) > MAX_EVENT_DAYS) {
      return `Events can be at most ${MAX_EVENT_DAYS} days long.`;
    }
    return null;
  });
  protected readonly lengthLabel = computed(() => {
    if (!this.startDate() || !this.endDate() || this.rangeError()) {
      return '';
    }
    const days = daysBetween(this.startDate(), this.endDate()) + 1;
    return days > 1 ? `${days} days` : '';
  });

  ngOnInit(): void {
    const event = this.event();
    if (event) {
      const start = new Date(event.startAt);
      const end = new Date(event.endAt);
      this.title.set(event.title);
      this.allDay.set(event.allDay);
      this.startDate.set(dayKey(start));
      this.endDate.set(dayKey(end));
      if (!event.allDay) {
        this.startTime.set(timeValue(start));
        this.endTime.set(timeValue(end));
      }
      this.color.set(event.color);
      this.description.set(event.description ?? '');
    } else {
      const day = this.initialDate() ?? dayKey(new Date());
      this.startDate.set(day);
      this.endDate.set(day);
    }
  }

  /** Moving the start date moves the end date by the same amount, keeping the length. */
  protected changeStartDate(value: string): void {
    const previous = this.startDate();
    this.startDate.set(value);
    if (previous && value && this.endDate()) {
      this.endDate.set(addDaysToKey(this.endDate(), daysBetween(previous, value)));
    }
  }

  protected close(): void {
    this.dialog().close();
  }

  protected async save(): Promise<void> {
    this.submitted.set(true);
    const title = this.title().trim();
    const start = this.start();
    const end = this.end();
    if (!title || !start || !end || this.rangeError() || this.saving()) {
      return;
    }
    this.saving.set(true);
    const request = {
      title,
      description: this.description().trim(),
      startAt: toOffsetIso(start),
      endAt: toOffsetIso(end),
      color: this.color(),
      allDay: this.allDay(),
    };
    const ok = this.isEdit()
      ? await this.store.updateEvent(this.eventId()!, request)
      : (await this.store.createEvent(request)) != null;
    this.saving.set(false);
    if (ok) {
      this.close();
    }
  }

  protected async delete(): Promise<void> {
    if (!this.confirmingDelete()) {
      this.confirmingDelete.set(true);
      return;
    }
    if (await this.store.deleteEvent(this.eventId()!)) {
      this.close();
    }
  }
}
