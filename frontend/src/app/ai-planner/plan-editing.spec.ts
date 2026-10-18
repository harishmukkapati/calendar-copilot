import { PlanDraft } from '../core/models';
import { toCreatePlanRequest, toEditable } from './plan-editing';

const draft: PlanDraft = {
  summary: 'Essay',
  model: 'gemini-test',
  documents: [],
  uncertainties: [],
  requirements: [
    { id: 'R1', text: 'Cite two readings', documentId: 'D1', documentName: 'rubric.pdf', quote: 'Cite at least two', verification: 'VERIFIED' },
    { id: 'R2', text: 'Made-up rule', documentId: 'D1', documentName: 'rubric.pdf', quote: 'nope', verification: 'NOT_FOUND' },
  ],
  tasks: [
    {
      title: 'Research',
      description: 'Find sources',
      basis: 'SOURCE',
      requirementIds: ['R1', 'R2'],
      dueAt: '2026-10-22T03:59:00Z',
      subtasks: [
        { title: 'Reread readings', basis: 'SOURCE', requirementIds: ['R1'] },
        { title: '   ', basis: 'SUGGESTED', requirementIds: [] },
      ],
    },
    { title: '', description: null, basis: 'SUGGESTED', requirementIds: [], dueAt: null, subtasks: [] },
  ],
};

describe('plan-editing', () => {
  it('maps the edited draft to a confirm request with source references', () => {
    const editable = toEditable(draft);
    editable[0].title = '  Research sources ';

    const request = toCreatePlanRequest(editable, draft.requirements, {
      title: 'Paper 4',
      description: null,
      dueAt: '2026-10-30T23:59:00-04:00',
    });

    expect(request.title).toBe('Paper 4');
    expect(request.tasks.length).toBe(1); // blank task dropped
    expect(request.tasks[0].title).toBe('Research sources');
    // Verified requirement is kept as a reference; the unverified one is not persisted.
    expect(request.tasks[0].description).toBe('Find sources\nRequirement (rubric.pdf): Cite two readings');
    expect(request.tasks[0].subtasks).toEqual([
      { title: 'Reread readings', description: 'Requirement (rubric.pdf): Cite two readings' },
    ]);
    expect(request.tasks[0].dueAt).toMatch(/^2026-10-2[12]T23:59:00[+-]\d{2}:\d{2}$/);
  });

  it('attaches to an existing task when breaking one down', () => {
    const request = toCreatePlanRequest(toEditable(draft), draft.requirements, { parentTaskId: 42 });

    expect(request.parentTaskId).toBe(42);
    expect(request.title).toBeUndefined();
  });
});
