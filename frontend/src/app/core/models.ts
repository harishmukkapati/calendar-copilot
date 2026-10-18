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
  /** All-day events are stored as local 00:00 on the first day to 23:59 on the last. */
  allDay: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEventRequest {
  title: string;
  description?: string | null;
  startAt: string;
  endAt: string;
  color?: EventColor;
  allDay?: boolean;
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

// ---- AI planner ----

export type DocumentCategory = 'INSTRUCTIONS' | 'RUBRIC' | 'SUPPORTING';
export type Basis = 'SOURCE' | 'SUGGESTED';
/** VERIFIED: quote found in the document. NOT_FOUND: possible hallucination. UNVERIFIABLE: image/scan. */
export type Verification = 'VERIFIED' | 'NOT_FOUND' | 'UNVERIFIABLE';

export interface PlanRequirement {
  id: string;
  text: string;
  documentId: string | null;
  documentName: string | null;
  quote: string | null;
  verification: Verification;
}

export interface DraftSubtask {
  title: string;
  basis: Basis;
  requirementIds: string[];
}

export interface DraftTask {
  title: string;
  description: string | null;
  basis: Basis;
  requirementIds: string[];
  dueAt: string | null;
  subtasks: DraftSubtask[];
}

export interface PlanDraft {
  summary: string | null;
  requirements: PlanRequirement[];
  tasks: DraftTask[];
  uncertainties: string[];
  documents: { id: string; name: string; category: DocumentCategory; kind: string; truncated: boolean }[];
  model: string;
}

export interface CreatePlanRequest {
  parentTaskId?: number;
  title?: string;
  description?: string | null;
  dueAt?: string | null;
  tasks: {
    title: string;
    description?: string | null;
    dueAt?: string | null;
    subtasks: { title: string; description?: string | null }[];
  }[];
}
