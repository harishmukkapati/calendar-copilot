import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CalendarStore, applyTaskUpdate } from './calendar-store';
import { dayKeyOf } from './date-utils';
import { Task } from './models';
import { WorkspaceService } from './workspace.service';

function task(overrides: Partial<Task>): Task {
  return {
    id: 1,
    parentId: null,
    title: 'Task',
    description: null,
    status: 'TODO',
    origin: 'MANUAL',
    startAt: null,
    dueAt: null,
    sortOrder: 0,
    createdAt: '2026-10-18T00:00:00Z',
    updatedAt: '2026-10-18T00:00:00Z',
    ...overrides,
  };
}

async function flushMicrotasks(): Promise<void> {
  for (let i = 0; i < 10; i++) {
    await Promise.resolve();
  }
}

describe('CalendarStore', () => {
  let store: CalendarStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: WorkspaceService, useValue: { ensure: async () => 'ws-1', id: 'ws-1' } },
      ],
    });
    store = TestBed.inject(CalendarStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('builds the hierarchy and counts progress across all levels', () => {
    store.tasks.set([
      task({ id: 1, title: 'Paper' }),
      task({ id: 2, parentId: 1, sortOrder: 1, status: 'DONE' }),
      task({ id: 3, parentId: 1, sortOrder: 0 }),
      task({ id: 4, parentId: 3, status: 'DONE' }),
    ]);

    expect(store.childrenOf().get(1)!.map((t) => t.id)).toEqual([3, 2]);
    expect(store.progress().get(1)).toEqual({ done: 2, total: 3 });
    expect(store.progress().get(3)).toEqual({ done: 1, total: 1 });
    expect(store.ancestorsOf(store.taskById().get(4)!).map((t) => t.id)).toEqual([1, 3]);
  });

  it('completes a task immediately and rolls back if the server rejects it', async () => {
    store.tasks.set([task({ id: 7 })]);

    const result = store.toggleComplete(store.tasks()[0]);
    expect(store.tasks()[0].status).toBe('DONE');

    http.expectOne({ method: 'PATCH', url: '/api/tasks/7' }).flush(
      { status: 500, detail: 'boom' },
      { status: 500, statusText: 'Server Error' },
    );

    expect(await result).toBe(false);
    expect(store.tasks()[0].status).toBe('TODO');
    expect(store.error()).toBe('Could not save the task.');

    // After a failure the store reloads from the server to fix any drift.
    await flushMicrotasks();
    http.expectOne('/api/tasks').flush([task({ id: 7, title: 'From server' })]);
    http.expectOne('/api/events').flush([]);
    await flushMicrotasks();
    expect(store.tasks()[0].title).toBe('From server');
  });

  it('sends rapid edits to the same task in order and ignores superseded responses', async () => {
    store.tasks.set([task({ id: 5 })]);

    const first = store.toggleComplete(store.tasks()[0]); // -> DONE
    const second = store.toggleComplete(store.tasks()[0]); // -> TODO
    expect(store.tasks()[0].status).toBe('TODO'); // UI reflects the latest intent immediately

    // Only the first PATCH is on the wire; the second waits for it.
    const req1 = http.expectOne({ method: 'PATCH', url: '/api/tasks/5' });
    expect(req1.request.body).toEqual({ status: 'DONE' });
    req1.flush(task({ id: 5, status: 'DONE' }));
    await first;
    expect(store.tasks()[0].status).toBe('TODO'); // stale response did not flicker the UI

    await flushMicrotasks();
    const req2 = http.expectOne({ method: 'PATCH', url: '/api/tasks/5' });
    expect(req2.request.body).toEqual({ status: 'TODO' });
    req2.flush(task({ id: 5, status: 'TODO', title: 'Saved' }));
    await second;
    expect(store.tasks()[0]).toMatchObject({ status: 'TODO', title: 'Saved' });
  });

  it('does not serialize writes to different tasks', () => {
    store.tasks.set([task({ id: 1 }), task({ id: 2 })]);

    store.toggleComplete(store.tasks()[0]);
    store.toggleComplete(store.tasks()[1]);

    http.expectOne('/api/tasks/1').flush(task({ id: 1, status: 'DONE' }));
    http.expectOne('/api/tasks/2').flush(task({ id: 2, status: 'DONE' }));
  });

  it('removes a whole subtree optimistically when deleting a parent', async () => {
    store.tasks.set([task({ id: 1 }), task({ id: 2, parentId: 1 }), task({ id: 3, parentId: 2 }), task({ id: 9 })]);

    const result = store.deleteTask(1);
    expect(store.tasks().map((t) => t.id)).toEqual([9]);

    http.expectOne({ method: 'DELETE', url: '/api/tasks/1' }).flush(null, { status: 204, statusText: 'No Content' });
    expect(await result).toBe(true);
  });

  it('shows the server validation message for field errors', async () => {
    const result = store.createTask({ title: '' });
    http.expectOne('/api/tasks').flush(
      { status: 400, detail: 'Validation failed.', errors: { title: 'must not be blank' } },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(await result).toBeNull();
    expect(store.error()).toBe('Title must not be blank.');
  });

  it('adds confirmed plan tasks to the calendar', async () => {
    store.tasks.set([task({ id: 1 })]);

    const result = store.confirmPlan({ title: 'Paper', tasks: [{ title: 'Step', subtasks: [] }] });
    http.expectOne({ method: 'POST', url: '/api/ai/confirm-plan' }).flush([
      task({ id: 10, title: 'Paper' }),
      task({ id: 11, parentId: 10, title: 'Step', origin: 'AI' }),
    ]);

    expect((await result)?.length).toBe(2);
    expect(store.childrenOf().get(10)?.map((t) => t.title)).toEqual(['Step']);
  });

  it('moves a dragged task to another day at the same local time', async () => {
    const due = new Date(2026, 9, 20, 17, 30); // Oct 20, 5:30 pm local
    const start = new Date(2026, 9, 19, 9, 0);
    store.tasks.set([task({ id: 3, dueAt: due.toISOString(), startAt: start.toISOString() })]);

    const result = store.moveTask(store.tasks()[0], '2026-10-23');
    const movedDue = new Date(store.tasks()[0].dueAt!);
    expect(movedDue.getDate()).toBe(23);
    expect(movedDue.getHours()).toBe(17); // optimistic, before the server answers

    const req = http.expectOne({ method: 'PATCH', url: '/api/tasks/3' });
    expect(req.request.body.dueAt).toMatch(/^2026-10-23T17:30:00[+-]\d{2}:\d{2}$/);
    expect(req.request.body.startAt).toMatch(/^2026-10-22T09:00:00[+-]\d{2}:\d{2}$/); // shifted by 3 days too
    req.flush(task({ id: 3, dueAt: movedDue.toISOString() }));
    expect(await result).toBe(true);
  });

  it('moves a dragged event keeping its duration, and ignores drops on the same day', async () => {
    const start = new Date(2026, 9, 21, 15, 0);
    const end = new Date(2026, 9, 21, 16, 30);
    store.events.set([
      { id: 8, title: 'Meeting', description: null, color: 'blue', allDay: false, startAt: start.toISOString(), endAt: end.toISOString(), createdAt: '', updatedAt: '' },
    ]);

    expect(await store.moveEvent(store.events()[0], '2026-10-21')).toBe(true);
    http.expectNone('/api/events/8');

    const result = store.moveEvent(store.events()[0], '2026-10-19');
    const req = http.expectOne({ method: 'PATCH', url: '/api/events/8' });
    expect(req.request.body.startAt).toMatch(/^2026-10-19T15:00:00/);
    expect(req.request.body.endAt).toMatch(/^2026-10-19T16:30:00/);
    expect(dayKeyOf(store.events()[0].startAt)).toBe('2026-10-19'); // optimistic
    req.flush({ ...store.events()[0] });
    expect(await result).toBe(true);
  });

  it('moves a multi-day event as a whole and marks every covered day as busy', async () => {
    const start = new Date(2026, 9, 20, 18, 0); // Tue 6 pm
    const end = new Date(2026, 9, 22, 14, 0); // Thu 2 pm
    store.events.set([
      { id: 9, title: 'Conference', description: null, color: 'purple', allDay: false, startAt: start.toISOString(), endAt: end.toISOString(), createdAt: '', updatedAt: '' },
    ]);
    expect(store.spanningEvents().map((e) => e.id)).toEqual([9]);
    expect(store.eventsByDay().size).toBe(0); // not drawn as a single-day chip
    expect(['2026-10-20', '2026-10-21', '2026-10-22'].every((d) => store.busyDays().has(d))).toBe(true);

    const result = store.moveEvent(store.events()[0], '2026-10-27');
    const req = http.expectOne({ method: 'PATCH', url: '/api/events/9' });
    expect(req.request.body.startAt).toMatch(/^2026-10-27T18:00:00/);
    expect(req.request.body.endAt).toMatch(/^2026-10-29T14:00:00/); // same 2-day length
    req.flush({ ...store.events()[0] });
    expect(await result).toBe(true);
  });

  it('mirrors PATCH semantics for optimistic updates', () => {
    const before = task({ dueAt: '2026-10-20T12:00:00Z', description: 'Notes' });

    expect(applyTaskUpdate(before, { clearDueAt: true }).dueAt).toBeNull();
    expect(applyTaskUpdate(before, { description: '' }).description).toBeNull();
    expect(applyTaskUpdate(before, { title: 'Renamed' }).dueAt).toBe('2026-10-20T12:00:00Z');
  });
});
