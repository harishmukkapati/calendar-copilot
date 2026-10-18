import { combineDateTime, dayKey, toOffsetIso } from '../core/date-utils';
import { Basis, CreatePlanRequest, PlanDraft, PlanRequirement } from '../core/models';

/** Editable copy of a draft. `key` gives each row a stable identity for @for tracking. */
export interface EditableSubtask {
  key: number;
  title: string;
  basis: Basis;
  requirementIds: string[];
}

export interface EditableTask {
  key: number;
  title: string;
  description: string;
  basis: Basis;
  requirementIds: string[];
  /** YYYY-MM-DD in local time, or '' for no date. */
  dueDate: string;
  subtasks: EditableSubtask[];
}

let nextKey = 1;
export function newKey(): number {
  return nextKey++;
}

export function toEditable(draft: PlanDraft): EditableTask[] {
  return draft.tasks.map((t) => ({
    key: newKey(),
    title: t.title,
    description: t.description ?? '',
    basis: t.basis,
    requirementIds: t.requirementIds,
    dueDate: t.dueAt ? dayKey(new Date(t.dueAt)) : '',
    subtasks: t.subtasks.map((s) => ({ key: newKey(), ...s })),
  }));
}

/**
 * Builds the confirm request from what the user kept and edited. Linked requirements
 * are written into the task description so the source reference survives after saving.
 */
export function toCreatePlanRequest(
  tasks: EditableTask[],
  requirements: PlanRequirement[],
  target: { parentTaskId: number } | { title: string; description: string | null; dueAt: string | null },
): CreatePlanRequest {
  const byId = new Map(requirements.map((r) => [r.id, r]));
  const request: CreatePlanRequest = {
    ...target,
    tasks: tasks
      .filter((t) => t.title.trim())
      .map((t) => ({
        title: t.title.trim(),
        description: withSources(t.description, t.requirementIds, byId),
        dueAt: t.dueDate ? toOffsetIso(combineDateTime(t.dueDate, '23:59')) : null,
        subtasks: t.subtasks
          .filter((s) => s.title.trim())
          .map((s) => ({ title: s.title.trim(), description: withSources('', s.requirementIds, byId) })),
      })),
  };
  return request;
}

function withSources(description: string, ids: string[], byId: Map<string, PlanRequirement>): string | null {
  const lines = ids
    .map((id) => byId.get(id))
    .filter((r): r is PlanRequirement => !!r && r.verification !== 'NOT_FOUND')
    .map((r) => `Requirement${r.documentName ? ` (${r.documentName})` : ''}: ${r.text}`);
  const text = [description.trim(), ...lines].filter(Boolean).join('\n');
  return text || null;
}
