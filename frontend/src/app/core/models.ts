export type TaskStatus = 'TODO' | 'DONE';
export type TaskOrigin = 'MANUAL' | 'AI';

/** Mirrors the backend TaskResponse. Dates are ISO-8601 instants (UTC). */
export interface Task {
  id: number;
  parentId: number | null;
  title: string;
  description: string | null;
  status: TaskStatus;
  origin: TaskOrigin;
  startAt: string | null;
  dueAt: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaskRequest {
  title: string;
  description?: string | null;
  parentId?: number | null;
  startAt?: string | null;
  dueAt?: string | null;
  sortOrder?: number;
}

/** Partial update; null/absent fields are unchanged. Dates are cleared explicitly. */
export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  status?: TaskStatus;
  startAt?: string;
  dueAt?: string;
  sortOrder?: number;
  clearStartAt?: boolean;
  clearDueAt?: boolean;
}

export const EVENT_COLORS = ['blue', 'green', 'purple', 'orange', 'red', 'gray'] as const;
export type EventColor = (typeof EVENT_COLORS)[number];

export interface CalendarEvent {
  id: number;
  title: string;
  description: string | null;
  startAt: string;
  endAt: string;
  color: EventColor;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEventRequest {
  title: string;
  description?: string | null;
  startAt: string;
  endAt: string;
  color?: EventColor;
}

export type UpdateEventRequest = Partial<CreateEventRequest>;

export interface Workspace {
  id: string;
  timeZone: string;
  createdAt: string;
}

/** RFC 7807 body returned by the backend for errors. */
export interface ProblemDetail {
  status: number;
  detail?: string;
  errors?: Record<string, string>;
}
