import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { CalendarStore } from '../core/calendar-store';
import { formatDueDate } from '../core/date-utils';
import { inputChecked, inputValue } from '../core/dom';
import { Icon } from '../core/icon';
import { Task } from '../core/models';

/** Matches the backend's app.task.max-depth: root task = 1. */
export const MAX_TASK_DEPTH = 3;

/**
 * Renders the children of one task and recurses for deeper levels. Every action
 * here is saved immediately (optimistically) through the store.
 */
@Component({
  selector: 'app-subtask-tree',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="tree" [class.nested]="depth() > 2">
      @for (task of children(); track task.id) {
        <li>
          <div class="row" [class.done]="task.status === 'DONE'">
            @if (hasChildren(task)) {
              <button
                class="toggle"
                type="button"
                [class.collapsed]="collapsed().has(task.id)"
                [attr.aria-expanded]="!collapsed().has(task.id)"
                [attr.aria-label]="(collapsed().has(task.id) ? 'Expand ' : 'Collapse ') + task.title"
                (click)="toggleCollapsed(task.id)"
              >
                <app-icon name="chevronDown" />
              </button>
            } @else {
              <span class="toggle-spacer"></span>
            }
            <button
              class="check"
              type="button"
              [class.done]="task.status === 'DONE'"
              [attr.aria-label]="(task.status === 'DONE' ? 'Mark incomplete: ' : 'Mark complete: ') + task.title"
              (click)="store.toggleComplete(task)"
            >
              <app-icon name="check" [strokeWidth]="3.5" />
            </button>
            <button class="title" type="button" title="Open subtask" (click)="openTask.emit(task.id)">
              {{ task.title }}
              @if (task.origin === 'AI') {
                <span class="badge-ai">AI</span>
              }
            </button>
            @if (task.dueAt) {
              <span class="due">{{ formatDueDate(task.dueAt) }}</span>
            }
            <span class="actions">
              @if (depth() < maxDepth) {
                <button class="icon-btn sm" type="button" [attr.aria-label]="'Add subtask to ' + task.title"
                  (click)="startAdding(task.id)">
                  <app-icon name="plus" />
                </button>
              }
              <button class="icon-btn sm" type="button" [attr.aria-label]="'Delete ' + task.title"
                (click)="store.deleteTask(task.id)">
                <app-icon name="trash" />
              </button>
            </span>
          </div>

          @if (!collapsed().has(task.id) && (hasChildren(task) || addingTo() === task.id)) {
            <app-subtask-tree [parentId]="task.id" [depth]="depth() + 1" [autoAdd]="addingTo() === task.id"
              (openTask)="openTask.emit($event)" (addDone)="addingTo.set(null)" />
          }
        </li>
      }
    </ul>

    @if (showAddInput()) {
      <div class="add">
        <span class="toggle-spacer"></span>
        <app-icon name="plus" class="add-icon" />
        <input
          #addInput
          class="add-input"
          name="newSubtask"
          [placeholder]="depth() === 2 ? 'Add a subtask' : 'Add a step'"
          aria-label="New subtask title"
          maxlength="200"
          [value]="newTitle()" (input)="newTitle.set(inputValue($event))"
          (keydown.enter)="add($event)"
          (keydown.escape)="cancelAdd($event)"
          (blur)="onBlur()"
        />
      </div>
    }
  `,
  styleUrl: './subtask-tree.css',
})
export class SubtaskTree {
  protected readonly inputValue = inputValue;
  protected readonly inputChecked = inputChecked;
  protected readonly store = inject(CalendarStore);

  readonly parentId = input.required<number>();
  /** Depth of the rows rendered by this instance (direct subtasks of a root task = 2). */
  readonly depth = input(2);
  /** Nested instances are opened by the "+" button and show their input right away. */
  readonly autoAdd = input(false);
  readonly openTask = output<number>();
  readonly addDone = output<void>();

  protected readonly maxDepth = MAX_TASK_DEPTH;
  protected readonly formatDueDate = formatDueDate;
  protected readonly collapsed = signal(new Set<number>());
  protected readonly addingTo = signal<number | null>(null);
  protected readonly newTitle = signal('');
  private readonly addInput = viewChild<ElementRef<HTMLInputElement>>('addInput');

  constructor() {
    afterNextRender(() => {
      if (this.autoAdd()) {
        this.addInput()?.nativeElement.focus();
      }
    });
  }

  protected readonly children = computed(() => this.store.childrenOf().get(this.parentId()) ?? []);
  /** The top level always shows an add input; nested levels only while adding. */
  protected readonly showAddInput = computed(() => this.depth() === 2 || this.autoAdd());

  protected hasChildren(task: Task): boolean {
    return (this.store.childrenOf().get(task.id)?.length ?? 0) > 0;
  }

  protected toggleCollapsed(id: number): void {
    this.collapsed.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  protected startAdding(id: number): void {
    this.collapsed.update((set) => {
      const next = new Set(set);
      next.delete(id);
      return next;
    });
    this.addingTo.set(id);
  }

  protected async add(event: Event): Promise<void> {
    event.preventDefault();
    const title = this.newTitle().trim();
    if (!title) {
      return;
    }
    this.newTitle.set('');
    (event.target as HTMLInputElement).value = ''; // see TaskModal.addDraftSubtask
    await this.store.createTask({ title, parentId: this.parentId() });
  }

  protected cancelAdd(event: Event): void {
    event.preventDefault();
    event.stopPropagation(); // keep Escape from closing the whole dialog
    this.newTitle.set('');
    this.addDone.emit();
  }

  protected onBlur(): void {
    if (this.autoAdd() && !this.newTitle().trim()) {
      this.addDone.emit();
    }
  }
}
