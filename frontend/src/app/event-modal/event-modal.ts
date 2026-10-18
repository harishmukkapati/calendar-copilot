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
import { combineDateTime, dayKey, timeValue, toOffsetIso } from '../core/date-utils';
import { inputChecked, inputValue } from '../core/dom';
import { Icon } from '../core/icon';
import { ModalDialog } from '../core/modal-dialog';
import { CalendarEvent, EVENT_COLORS, EventColor } from '../core/models';

@Component({
  selector: 'app-event-modal',
  imports: [Icon, ModalDialog],
  templateUrl: './event-modal.html',
  styleUrl: './event-modal.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventModal implements OnInit {
  protected readonly inputValue = inputValue;
  protected readonly inputChecked = inputChecked;
  private readonly store = inject(CalendarStore);

  readonly eventId = input<number | null>(null);
  readonly initialDate = input<string | null>(null);
  readonly closed = output<void>();
  readonly switchToTask = output<string>();

  private readonly dialog = viewChild.required(ModalDialog);

  protected readonly colors = EVENT_COLORS;
  protected readonly title = signal('');
  protected readonly date = signal('');
  protected readonly start = signal('09:00');
  protected readonly end = signal('10:00');
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
  protected readonly timeError = computed(() => this.submitted() && this.end() < this.start());

  ngOnInit(): void {
    const event = this.event();
    if (event) {
      const start = new Date(event.startAt);
      this.title.set(event.title);
      this.date.set(dayKey(start));
      this.start.set(timeValue(start));
      this.end.set(timeValue(new Date(event.endAt)));
      this.color.set(event.color);
      this.description.set(event.description ?? '');
    } else {
      this.date.set(this.initialDate() ?? dayKey(new Date()));
    }
  }

  protected close(): void {
    this.dialog().close();
  }

  protected async save(): Promise<void> {
    this.submitted.set(true);
    const title = this.title().trim();
    if (!title || !this.date() || this.timeError() || this.saving()) {
      return;
    }
    this.saving.set(true);
    const request = {
      title,
      description: this.description().trim(),
      startAt: toOffsetIso(combineDateTime(this.date(), this.start())),
      endAt: toOffsetIso(combineDateTime(this.date(), this.end())),
      color: this.color(),
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
