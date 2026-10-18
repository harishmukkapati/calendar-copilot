import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CalendarStore, applyTaskUpdate } from './calendar-store';
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

  it('mirrors PATCH semantics for optimistic updates', () => {
    const before = task({ dueAt: '2026-10-20T12:00:00Z', description: 'Notes' });

    expect(applyTaskUpdate(before, { clearDueAt: true }).dueAt).toBeNull();
    expect(applyTaskUpdate(before, { description: '' }).description).toBeNull();
    expect(applyTaskUpdate(before, { title: 'Renamed' }).dueAt).toBe('2026-10-20T12:00:00Z');
  });
});
