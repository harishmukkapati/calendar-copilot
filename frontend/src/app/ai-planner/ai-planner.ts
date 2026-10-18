import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, input, output, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { ApiService } from '../core/api.service';
import { CalendarStore } from '../core/calendar-store';
import { inputValue } from '../core/dom';
import { Icon } from '../core/icon';
import { downscaleImage } from '../core/image-resize';
import { Basis, DocumentCategory, PlanDraft, PlanRequirement, Verification } from '../core/models';
import { EditableSubtask, EditableTask, newKey, toCreatePlanRequest, toEditable } from './plan-editing';

const MAX_FILES = 6;
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const MAX_TASKS = 10;
const MAX_SUBTASKS = 10;
const ACCEPTED = /\.(pdf|png|jpe?g|txt)$/i;

/** Shown while waiting; advances on a timer because the server does one request. */
const STAGES = [
  { at: 0, label: 'Reading your files…' },
  { at: 2500, label: 'Finding the requirements…' },
  { at: 7000, label: 'Building your plan…' },
  { at: 14000, label: 'Checking citations against your files…' },
  { at: 25000, label: 'Almost done. Large files take a little longer…' },
];

interface UploadSlot {
  category: DocumentCategory;
  field: string;
  label: string;
  hint: string;
}

/**
 * Generate -> review/edit -> confirm. Nothing is saved until the user confirms;
 * generated items are visually marked as drafts until then.
 */
@Component({
  selector: 'app-ai-planner',
  imports: [Icon],
  templateUrl: './ai-planner.html',
  styleUrl: './ai-planner.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiPlanner implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly store = inject(CalendarStore);

  /** Values from the task form the planner sits in. */
  readonly title = input.required<string>();
  readonly description = input('');
  readonly dueAt = input<string | null>(null);
  /** Set when breaking down an existing task: the plan is added under it. */
  readonly parentTaskId = input<number | null>(null);
  readonly saved = output<void>();
  readonly back = output<void>();

  protected readonly slots: UploadSlot[] = [
    { category: 'INSTRUCTIONS', field: 'instructions', label: 'Assignment instructions', hint: 'PDF, screenshot or text' },
    { category: 'RUBRIC', field: 'rubric', label: 'Rubric', hint: 'Grading criteria or requirements' },
    { category: 'SUPPORTING', field: 'supporting', label: 'Supporting materials', hint: 'Slides, readings, notes' },
  ];

  protected readonly phase = signal<'input' | 'generating' | 'review' | 'saving'>('input');
  protected readonly files = signal<Record<DocumentCategory, File[]>>({ INSTRUCTIONS: [], RUBRIC: [], SUPPORTING: [] });
  protected readonly context = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly stage = signal(STAGES[0].label);
  protected readonly dragOver = signal<DocumentCategory | null>(null);

  protected readonly draft = signal<PlanDraft | null>(null);
  protected readonly tasks = signal<EditableTask[]>([]);

  protected readonly inputValue = inputValue;
  protected readonly maxTasks = MAX_TASKS;
  protected readonly maxSubtasks = MAX_SUBTASKS;

  protected readonly fileCount = computed(() => Object.values(this.files()).flat().length);
  protected readonly requirementsById = computed(
    () => new Map((this.draft()?.requirements ?? []).map((r) => [r.id, r])),
  );
  protected readonly itemCount = computed(() =>
    this.tasks().reduce((n, t) => n + (t.title.trim() ? 1 : 0) + t.subtasks.filter((s) => s.title.trim()).length, 0),
  );

  private request: Subscription | null = null;
  private timers: ReturnType<typeof setTimeout>[] = [];

  ngOnDestroy(): void {
    this.cancel();
  }

  // ---- Uploads ----

  protected async addFiles(category: DocumentCategory, list: FileList | null): Promise<void> {
    this.error.set(null);
    for (const original of Array.from(list ?? [])) {
      if (!ACCEPTED.test(original.name)) {
        this.error.set(`${original.name}: only PDF, PNG, JPEG and TXT files are supported.`);
        continue;
      }
      if (this.fileCount() >= MAX_FILES) {
        this.error.set(`You can upload up to ${MAX_FILES} files.`);
        return;
      }
      const file = await downscaleImage(original);
      if (file.size > MAX_FILE_BYTES) {
        this.error.set(`${original.name} is larger than 5 MB.`);
        continue;
      }
      this.files.update((all) => ({ ...all, [category]: [...all[category], file] }));
    }
  }

  protected onPick(category: DocumentCategory, event: Event): void {
    const inputEl = event.target as HTMLInputElement;
    this.addFiles(category, inputEl.files).finally(() => (inputEl.value = ''));
  }

  protected onDrop(category: DocumentCategory, event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(null);
    this.addFiles(category, event.dataTransfer?.files ?? null);
  }

  protected onDragOver(category: DocumentCategory, event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(category);
  }

  protected removeFile(category: DocumentCategory, index: number): void {
    this.files.update((all) => ({ ...all, [category]: all[category].filter((_, i) => i !== index) }));
  }

  protected formatSize(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  // ---- Generate ----

  protected generate(): void {
    const title = this.title().trim();
    if (!title) {
      this.error.set('Add a task title above first. It tells the AI what to plan.');
      return;
    }
    this.error.set(null);
    const form = new FormData();
    form.append('title', title);
    if (this.dueAt()) form.append('dueAt', this.dueAt()!);
    if (this.context().trim()) form.append('context', this.context().trim());
    form.append('timeZone', Intl.DateTimeFormat().resolvedOptions().timeZone);
    for (const slot of this.slots) {
      for (const file of this.files()[slot.category]) {
        form.append(slot.field, file, file.name);
      }
    }

    this.phase.set('generating');
    this.startStages();
    this.request = this.api.generatePlan(form).subscribe({
      next: (draft) => {
        this.stopStages();
        this.draft.set(draft);
        this.tasks.set(toEditable(draft));
        this.phase.set('review');
      },
      error: (e) => {
        this.stopStages();
        this.error.set(CalendarStore.messageFor(e, 'Could not generate a plan. Please try again.'));
        this.phase.set('input');
      },
    });
  }

  /** Abandons the in-flight request (the HTTP call is aborted). */
  protected cancel(): void {
    this.request?.unsubscribe();
    this.request = null;
    this.stopStages();
    if (this.phase() === 'generating') {
      this.phase.set('input');
    }
  }

  protected startOver(): void {
    this.draft.set(null);
    this.tasks.set([]);
    this.error.set(null);
    this.phase.set('input');
  }

  private startStages(): void {
    this.stage.set(STAGES[0].label);
    this.timers = STAGES.slice(1).map((s) => setTimeout(() => this.stage.set(s.label), s.at));
  }

  private stopStages(): void {
    this.timers.forEach(clearTimeout);
    this.timers = [];
  }

  // ---- Review and edit ----

  protected updateTask(key: number, patch: Partial<EditableTask>): void {
    this.tasks.update((list) => list.map((t) => (t.key === key ? { ...t, ...patch } : t)));
  }

  protected removeTask(key: number): void {
    this.tasks.update((list) => list.filter((t) => t.key !== key));
  }

  protected addTask(): void {
    this.tasks.update((list) => [
      ...list,
      { key: newKey(), title: '', description: '', basis: 'SUGGESTED', requirementIds: [], dueDate: '', subtasks: [] },
    ]);
  }

  protected updateSubtask(taskKey: number, subKey: number, patch: Partial<EditableSubtask>): void {
    this.tasks.update((list) =>
      list.map((t) =>
        t.key === taskKey ? { ...t, subtasks: t.subtasks.map((s) => (s.key === subKey ? { ...s, ...patch } : s)) } : t,
      ),
    );
  }

  protected removeSubtask(taskKey: number, subKey: number): void {
    this.tasks.update((list) =>
      list.map((t) => (t.key === taskKey ? { ...t, subtasks: t.subtasks.filter((s) => s.key !== subKey) } : t)),
    );
  }

  protected addSubtask(taskKey: number): void {
    this.tasks.update((list) =>
      list.map((t) =>
        t.key === taskKey
          ? { ...t, subtasks: [...t.subtasks, { key: newKey(), title: '', basis: 'SUGGESTED', requirementIds: [] }] }
          : t,
      ),
    );
  }

  protected basisLabel(basis: Basis): string {
    return basis === 'SOURCE' ? 'From your files' : 'Suggested';
  }

  protected verificationLabel(r: PlanRequirement): string {
    const labels: Record<Verification, string> = {
      VERIFIED: `Found in ${r.documentName ?? 'your files'}`,
      NOT_FOUND: 'Not found in your files',
      UNVERIFIABLE: `From ${r.documentName ?? 'an image'}, not checked`,
    };
    return labels[r.verification];
  }

  protected requirementTooltip(id: string): string {
    const r = this.requirementsById().get(id);
    return r ? `${r.text}${r.quote ? ` — “${r.quote}”` : ''}` : id;
  }

  // ---- Confirm ----

  protected async confirm(): Promise<void> {
    const draft = this.draft();
    if (!draft || this.phase() === 'saving') {
      return; // guards against double submission
    }
    const kept = this.tasks().filter((t) => t.title.trim());
    if (kept.length === 0) {
      this.error.set('Keep at least one task, or cancel.');
      return;
    }
    const parentTaskId = this.parentTaskId();
    const target =
      parentTaskId != null
        ? { parentTaskId }
        : { title: this.title().trim(), description: this.description().trim() || null, dueAt: this.dueAt() };
    if (!('parentTaskId' in target) && !target.title) {
      this.error.set('Add a task title above before saving.');
      return;
    }

    this.error.set(null);
    this.phase.set('saving');
    const created = await this.store.confirmPlan(toCreatePlanRequest(kept, draft.requirements, target));
    if (created) {
      this.saved.emit();
    } else {
      // Show the reason inside the dialog (a toast would sit behind the modal backdrop).
      this.error.set(this.store.error() ?? 'Could not save the plan.');
      this.store.dismissError();
      this.phase.set('review');
    }
  }
}
