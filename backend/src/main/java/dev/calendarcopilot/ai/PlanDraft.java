package dev.calendarcopilot.ai;

import java.time.Instant;
import java.util.List;

/**
 * An AI-generated plan returned for review. Nothing here is saved until the user
 * confirms it through /api/ai/confirm-plan.
 */
public record PlanDraft(
        String summary,
        List<Requirement> requirements,
        List<DraftTask> tasks,
        List<String> uncertainties,
        List<DocumentSummary> documents,
        String model) {

    /** SOURCE: fulfils a requirement found in the documents. SUGGESTED: the model's recommendation. */
    public enum Basis {
        SOURCE, SUGGESTED
    }

    /**
     * VERIFIED: the quote was found in the document text. NOT_FOUND: it was not (possible hallucination).
     * UNVERIFIABLE: the source is an image or scanned PDF, so the server cannot check the quote.
     */
    public enum Verification {
        VERIFIED, NOT_FOUND, UNVERIFIABLE
    }

    public record Requirement(String id, String text, String documentId, String documentName, String quote,
            Verification verification) {
    }

    public record DraftTask(String title, String description, Basis basis, List<String> requirementIds,
            Instant dueAt, List<DraftSubtask> subtasks) {
    }

    public record DraftSubtask(String title, Basis basis, List<String> requirementIds) {
    }

    public record DocumentSummary(String id, String name, DocumentCategory category, SourceDocument.Kind kind,
            int characters, boolean truncated) {

        static DocumentSummary of(SourceDocument d) {
            return new DocumentSummary(d.id(), d.name(), d.category(), d.kind(),
                    d.hasText() ? d.text().length() : 0, d.truncated());
        }
    }
}
