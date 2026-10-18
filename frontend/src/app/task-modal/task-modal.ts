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
import { Task, UpdateTaskRequest } from '../core/models';
import { AiPlanner } from '../ai-planner/ai-planner';
import { MAX_TASK_DEPTH, SubtaskTree } from './subtask-tree';

const DEFAULT_DUE_TIME = '23:59';

/**
 * Create mode (no taskId): fields plus a simple list of subtasks that are saved
 * after the parent. Edit mode: fields are saved on "Save"; subtask changes save immediately.
 */
@Component({
  selector: 'app-task-modal',
  imports: [Icon, ModalDialog, SubtaskTree, AiPlanner],
  templateUrl: './task-modal.html',
  styleUrl: './task-modal.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TaskModal implements OnInit {
  protected readonly inputValue = inputValue;
  protected readonly inputChecked = inputChecked;
  protected readonly store = inject(CalendarStore);

  readonly taskId = input<number | null>(null);
  readonly initialDate = input<string | null>(null);
  readonly closed = output<void>();
  readonly openTask = output<number>();
  readonly switchToEvent = output<string>();

  private readonly dialog = viewChild.required(ModalDialog);

  protected readonly title = signal('');
  protected readonly date = signal('');
  protected readonly time = signal('');
  protected readonly description = signal('');
  protected readonly completed = signal(false);
  /** Create mode only. */
  protected readonly draftSubtasks = signal<string[]>([]);
  protected readonly newSubtask = signal('');

  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly confirmingDelete = signal(false);

  protected readonly task = computed<Task | undefined>(() => {
    const id = this.taskId();
    return id != null ? this.store.taskById().get(id) : undefined;
  });
  protected readonly isEdit = computed(() => this.taskId() != null);
  protected readonly ancestors = computed(() => {
    const task = this.task();
    return task ? this.store.ancestorsOf(task) : [];
  });
  protected readonly subtaskCount = computed(() => {
    const id = this.taskId();
    return id != null ? (this.store.progress().get(id)?.total ?? 0) : 0;
  });
  protected readonly titleError = computed(() => this.submitted() && !this.title().trim());

  /** Shows the AI planner in place of the manual subtask editor. */
  protected readonly aiMode = signal(false);
  /** The plan adds two levels below the task, so only tasks with room for them can be broken down. */
  protected readonly canUseAi = computed(() => !this.isEdit() || this.ancestors().length + 3 <= MAX_TASK_DEPTH);
  protected readonly dueIso = computed(() =>
    this.date() ? toOffsetIso(combineDateTime(this.date(), this.time() || DEFAULT_DUE_TIME)) : null,
  );

  ngOnInit(): void {
    const task = this.task();
    if (task) {
      this.title.set(task.title);
      this.description.set(task.description ?? '');
      this.completed.set(task.status === 'DONE');
      if (task.dueAt) {
        const due = new Date(task.dueAt);
        this.date.set(dayKey(due));
        this.time.set(timeValue(due));
      }
    } else {
      this.date.set(this.initialDate() ?? '');
    }
  }

  protected close(): void {
    this.dialog().close();
  }

  protected addDraftSubtask(event: Event): void {
    event.preventDefault();
    const title = this.newSubtask().trim();
    if (title) {
      this.draftSubtasks.update((list) => [...list, title]);
      this.newSubtask.set('');
      // Clear the element directly: if no render happened since the last keystroke,
      // the [value] binding still holds '' and would not write to the DOM.
      (event.target as HTMLInputElement).value = '';
    }
  }

  protected removeDraftSubtask(index: number): void {
    this.draftSubtasks.update((list) => list.filter((_, i) => i !== index));
  }

  protected async save(): Promise<void> {
    if (this.aiMode()) {
      return; // Enter inside the planner must not save the task without its plan
    }
    this.submitted.set(true);
    const title = this.title().trim();
    if (!title || this.saving()) {
      return;
    }
    this.saving.set(true);
    const dueAt = this.dueIso();
    const ok = this.isEdit() ? await this.saveEdit(title, dueAt) : await this.saveNew(title, dueAt);
    this.saving.set(false);
    if (ok) {
      this.close();
    }
  }

  private async saveNew(title: string, dueAt: string | null): Promise<boolean> {
    const pendingSubtask = this.newSubtask().trim();
    const subtasks = pendingSubtask ? [...this.draftSubtasks(), pendingSubtask] : this.draftSubtasks();
    const created = await this.store.createTask({
      title,
      description: this.description().trim() || null,
      dueAt,
    });
    if (!created) {
      return false;
    }
    // Sequential so subtasks keep the order the user typed them in.
    for (const subtaskTitle of subtasks) {
      await this.store.createTask({ title: subtaskTitle, parentId: created.id });
    }
    return true;
  }

  private saveEdit(title: string, dueAt: string | null): Promise<boolean> {
    const request: UpdateTaskRequest = {
      title,
      description: this.description().trim(),
      status: this.completed() ? 'DONE' : 'TODO',
    };
    if (dueAt) {
      request.dueAt = dueAt;
    } else {
      request.clearDueAt = true;
    }
    return this.store.updateTask(this.taskId()!, request);
  }

  protected async delete(): Promise<void> {
    if (!this.confirmingDelete()) {
      this.confirmingDelete.set(true);
      return;
    }
    if (await this.store.deleteTask(this.taskId()!)) {
      this.close();
    }
  }

  protected createEventInstead(): void {
    this.switchToEvent.emit(this.date() || dayKey(new Date()));
  }
}
