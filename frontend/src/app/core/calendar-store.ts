import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

import { ApiService } from './api.service';
import { dayKeyOf } from './date-utils';
import {
  CalendarEvent,
  CreateEventRequest,
  CreatePlanRequest,
  CreateTaskRequest,
  ProblemDetail,
  Task,
  UpdateEventRequest,
  UpdateTaskRequest,
} from './models';
import { WorkspaceService } from './workspace.service';

export interface Progress {
  done: number;
  total: number;
}

/**
 * Single source of truth for calendar data. Everything is loaded once, so moving
 * between months never hits the network. Edits, completes and deletes are applied
 * locally first (optimistic) and rolled back if the server rejects them.
 */
@Injectable({ providedIn: 'root' })
export class CalendarStore {
  private readonly api = inject(ApiService);
  private readonly workspace = inject(WorkspaceService);

  readonly tasks = signal<Task[]>([]);
  readonly events = signal<CalendarEvent[]>([]);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  /** Last user-facing error, shown as a toast. */
  readonly error = signal<string | null>(null);

  /** Pending write per item ("task:1"), so writes to one item reach the server in order. */
  private readonly queues = new Map<string, Promise<unknown>>();
  /** Latest write number per item; responses from older writes are ignored. */
  private readonly latestWrite = new Map<string, number>();
  private writeCounter = 0;

  readonly taskById = computed(() => new Map(this.tasks().map((t) => [t.id, t])));

  /** parentId (null for roots) -> children in display order. */
  readonly childrenOf = computed(() => {
    const map = new Map<number | null, Task[]>();
    for (const task of this.tasks()) {
      const list = map.get(task.parentId) ?? [];
      list.push(task);
      map.set(task.parentId, list);
    }
    for (const list of map.values()) {
      list.sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
    }
    return map;
  });

  /** Tasks with a due date grouped by local day. Subtasks without dates stay inside their parent. */
  readonly tasksByDay = computed(() => {
    const map = new Map<string, Task[]>();
    for (const task of this.tasks()) {
      if (task.dueAt) {
        const key = dayKeyOf(task.dueAt);
        map.set(key, [...(map.get(key) ?? []), task]);
      }
    }
    for (const list of map.values()) {
      list.sort((a, b) => a.dueAt!.localeCompare(b.dueAt!));
    }
    return map;
  });

  readonly eventsByDay = computed(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const event of this.events()) {
      const key = dayKeyOf(event.startAt);
      map.set(key, [...(map.get(key) ?? []), event]);
    }
    for (const list of map.values()) {
      list.sort((a, b) => a.startAt.localeCompare(b.startAt));
    }
    return map;
  });

  /** Completed / total descendants for each task that has subtasks. */
  readonly progress = computed(() => {
    const children = this.childrenOf();
    const result = new Map<number, Progress>();
    const visit = (task: Task): Progress => {
      const totals = { done: 0, total: 0 };
      for (const child of children.get(task.id) ?? []) {
        const sub = visit(child);
        totals.total += 1 + sub.total;
        totals.done += (child.status === 'DONE' ? 1 : 0) + sub.done;
      }
      if (totals.total > 0) {
        result.set(task.id, totals);
      }
      return totals;
    };
    (children.get(null) ?? []).forEach(visit);
    return result;
  });

  async load(): Promise<void> {
    this.status.set('loading');
    try {
      await this.workspace.ensure();
      const [tasks, events] = await Promise.all([this.api.listTasks(), this.api.listEvents()]);
      this.tasks.set(tasks);
      this.events.set(events);
      this.status.set('ready');
    } catch (e) {
      this.status.set('error');
      this.reportError(e, 'Could not load your calendar.');
    }
  }

  /** Replaces the guest workspace with a fresh copy of the demo data. */
  async resetDemo(): Promise<void> {
    this.status.set('loading');
    try {
      await this.workspace.reset();
    } catch (e) {
      this.status.set('error');
      this.reportError(e, 'Could not reset the demo.');
      return;
    }
    await this.load();
  }

  // ---- Tasks ----

  /** Not optimistic: the new task needs its server id before subtasks can reference it. */
  async createTask(request: CreateTaskRequest): Promise<Task | null> {
    try {
      const task = await this.api.createTask(request);
      this.tasks.update((list) => [...list, task]);
      return task;
    } catch (e) {
      this.reportError(e, 'Could not create the task.');
      return null;
    }
  }

  /** Saves a reviewed AI plan (one server transaction) and adds the new tasks locally. */
  async confirmPlan(request: CreatePlanRequest): Promise<Task[] | null> {
    try {
      const created = await this.api.confirmPlan(request);
      this.tasks.update((list) => [...list, ...created]);
      return created;
    } catch (e) {
      this.reportError(e, 'Could not save the plan.');
      return null;
    }
  }

  async updateTask(id: number, request: UpdateTaskRequest): Promise<boolean> {
    const before = this.taskById().get(id);
    if (!before) {
      return false;
    }
    const key = `task:${id}`;
    const write = this.startWrite(key);
    this.replaceTask(applyTaskUpdate(before, request));
    try {
      const saved = await this.enqueue(key, () => this.api.updateTask(id, request));
      if (this.isLatest(key, write)) {
        this.replaceTask(saved);
      }
      return true;
    } catch (e) {
      if (this.isLatest(key, write)) {
        this.replaceTask(before);
      }
      this.reportError(e, 'Could not save the task.');
      this.resync();
      return false;
    }
  }

  toggleComplete(task: Task): Promise<boolean> {
    return this.updateTask(task.id, { status: task.status === 'DONE' ? 'TODO' : 'DONE' });
  }

  /** Removes the task and its whole subtree, matching the server's cascade delete. */
  async deleteTask(id: number): Promise<boolean> {
    const snapshot = this.tasks();
    const removed = this.subtreeIds(id);
    this.tasks.set(snapshot.filter((t) => !removed.has(t.id)));
    try {
      await this.enqueue(`task:${id}`, () => this.api.deleteTask(id));
      return true;
    } catch (e) {
      this.tasks.set(snapshot);
      this.reportError(e, 'Could not delete the task.');
      return false;
    }
  }

  /** Root ancestor first, ending with the task's parent. */
  ancestorsOf(task: Task): Task[] {
    const byId = this.taskById();
    const chain: Task[] = [];
    let parent = task.parentId != null ? byId.get(task.parentId) : undefined;
    while (parent) {
      chain.unshift(parent);
      parent = parent.parentId != null ? byId.get(parent.parentId) : undefined;
    }
    return chain;
  }

  // ---- Events ----

  async createEvent(request: CreateEventRequest): Promise<CalendarEvent | null> {
    try {
      const event = await this.api.createEvent(request);
      this.events.update((list) => [...list, event]);
      return event;
    } catch (e) {
      this.reportError(e, 'Could not create the event.');
      return null;
    }
  }

  async updateEvent(id: number, request: UpdateEventRequest): Promise<boolean> {
    const before = this.events().find((e) => e.id === id);
    if (!before) {
      return false;
    }
    const key = `event:${id}`;
    const write = this.startWrite(key);
    this.replaceEvent({ ...before, ...stripUndefined(request) } as CalendarEvent);
    try {
      const saved = await this.enqueue(key, () => this.api.updateEvent(id, request));
      if (this.isLatest(key, write)) {
        this.replaceEvent(saved);
      }
      return true;
    } catch (e) {
      if (this.isLatest(key, write)) {
        this.replaceEvent(before);
      }
      this.reportError(e, 'Could not save the event.');
      this.resync();
      return false;
    }
  }

  async deleteEvent(id: number): Promise<boolean> {
    const snapshot = this.events();
    this.events.set(snapshot.filter((e) => e.id !== id));
    try {
      await this.enqueue(`event:${id}`, () => this.api.deleteEvent(id));
      return true;
    } catch (e) {
      this.events.set(snapshot);
      this.reportError(e, 'Could not delete the event.');
      return false;
    }
  }

  dismissError(): void {
    this.error.set(null);
  }

  /**
   * Runs writes for the same item one after another. Without this, two quick toggles
   * can be processed by the server in either order and leave the wrong final state.
   */
  private enqueue<T>(key: string, run: () => Promise<T>): Promise<T> {
    const previous = this.queues.get(key);
    // Nothing pending for this item: send right away instead of a microtask later.
    const next = previous ? previous.catch(() => undefined).then(run) : run();
    this.queues.set(key, next);
    next.then(
      () => this.queues.get(key) === next && this.queues.delete(key),
      () => this.queues.get(key) === next && this.queues.delete(key),
    );
    return next;
  }

  private startWrite(key: string): number {
    const write = ++this.writeCounter;
    this.latestWrite.set(key, write);
    return write;
  }

  /** Only the newest write's response may touch local state; older ones are already superseded. */
  private isLatest(key: string, write: number): boolean {
    return this.latestWrite.get(key) === write;
  }

  /**
   * After a failed write the local copy may disagree with the server (e.g. another tab
   * changed it), so reload once all pending writes have settled.
   */
  private async resync(): Promise<void> {
    await Promise.allSettled([...this.queues.values()]);
    try {
      const [tasks, events] = await Promise.all([this.api.listTasks(), this.api.listEvents()]);
      this.tasks.set(tasks);
      this.events.set(events);
    } catch {
      // Keep the current state; the error toast is already showing.
    }
  }

  private replaceTask(task: Task): void {
    this.tasks.update((list) => list.map((t) => (t.id === task.id ? task : t)));
  }

  private replaceEvent(event: CalendarEvent): void {
    this.events.update((list) => list.map((e) => (e.id === event.id ? event : e)));
  }

  private subtreeIds(rootId: number): Set<number> {
    const children = this.childrenOf();
    const ids = new Set<number>();
    const stack = [rootId];
    while (stack.length) {
      const id = stack.pop()!;
      ids.add(id);
      (children.get(id) ?? []).forEach((c) => stack.push(c.id));
    }
    return ids;
  }

  reportError(e: unknown, fallback: string): void {
    this.error.set(CalendarStore.messageFor(e, fallback));
  }

  /** User-facing message for an API error (problem detail when the server sent one). */
  static messageFor(e: unknown, fallback: string): string {
    const problem = e instanceof HttpErrorResponse ? (e.error as ProblemDetail | null) : null;
    if (problem?.errors) {
      const [field, message] = Object.entries(problem.errors)[0];
      return `${capitalize(field)} ${message}.`;
    }
    if (problem?.detail && (problem.status < 500 || [502, 503, 504].includes(problem.status))) {
      return problem.detail;
    }
    if (e instanceof HttpErrorResponse && e.status === 0) {
      return 'Network error. Check your connection and try again.';
    }
    return fallback;
  }
}

/** Mirrors the server's PATCH semantics for the optimistic local copy. */
export function applyTaskUpdate(task: Task, request: UpdateTaskRequest): Task {
  const next: Task = { ...task };
  if (request.title !== undefined) next.title = request.title;
  if (request.description !== undefined) next.description = request.description || null;
  if (request.status !== undefined) next.status = request.status;
  if (request.sortOrder !== undefined) next.sortOrder = request.sortOrder;
  if (request.clearStartAt) next.startAt = null;
  else if (request.startAt !== undefined) next.startAt = new Date(request.startAt).toISOString();
  if (request.clearDueAt) next.dueAt = null;
  else if (request.dueAt !== undefined) next.dueAt = new Date(request.dueAt).toISOString();
  return next;
}

function stripUndefined<T extends object>(value: T): Partial<T> {
  return Object.fromEntries(Object.entries(value).filter(([, v]) => v !== undefined)) as Partial<T>;
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
