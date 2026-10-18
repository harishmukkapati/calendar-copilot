package dev.calendarcopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.calendarcopilot.ai.PlanDraft.Basis;
import dev.calendarcopilot.ai.PlanDraft.Verification;
import dev.calendarcopilot.ai.PlanValidator.MalformedPlanException;
import tools.jackson.databind.json.JsonMapper;

class PlanValidatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final OffsetDateTime DUE = OffsetDateTime.parse("2026-10-30T23:59:00-04:00");

    private final PlanValidator validator = new PlanValidator(JsonMapper.builder().build(), new CitationVerifier());

    private final SourceDocument rubric = new SourceDocument("D1", "rubric.pdf", DocumentCategory.RUBRIC,
            SourceDocument.Kind.PDF_TEXT, "Papers must cite at least three peer-reviewed sources in MLA format.",
            null, "application/pdf", false);

    private PlanValidator.Context ctx() {
        return new PlanValidator.Context(List.of(rubric), DUE, LocalDate.of(2026, 10, 18), ZONE, "gemini-test");
    }

    @Test
    void verifiesCitationsAndDowngradesUnsupportedSourceClaims() {
        String output = """
                {"summary":"Argumentative essay.",
                 "requirements":[
                   {"id":"R1","text":"Cite three peer-reviewed sources","documentId":"D1",
                    "quote":"must cite at least three peer-reviewed sources"},
                   {"id":"R2","text":"At least 3000 words","documentId":"D1","quote":"must be at least 3000 words"},
                   {"id":"R3","text":"Ghost doc","documentId":"D9","quote":"anything at all here"}],
                 "tasks":[
                   {"title":"Find sources","description":"","basis":"SOURCE","requirementIds":["R1","R404"],
                    "suggestedDueDate":"2026-10-22","subtasks":[
                      {"title":"Search the library database","basis":"SUGGESTED","requirementIds":[]}]},
                   {"title":"Write 3000 words","description":"","basis":"SOURCE","requirementIds":["R2"],
                    "suggestedDueDate":"2026-10-30","subtasks":[]}],
                 "uncertainties":["Topic is not specified."]}
                """;

        PlanDraft draft = validator.toDraft(output, ctx());

        assertThat(draft.requirements()).extracting(PlanDraft.Requirement::verification)
                .containsExactly(Verification.VERIFIED, Verification.NOT_FOUND, Verification.NOT_FOUND);
        assertThat(draft.requirements().get(0).documentName()).isEqualTo("rubric.pdf");

        PlanDraft.DraftTask sources = draft.tasks().get(0);
        assertThat(sources.basis()).isEqualTo(Basis.SOURCE);
        assertThat(sources.requirementIds()).containsExactly("R1"); // unknown R404 dropped
        assertThat(sources.description()).isNull();
        assertThat(sources.dueAt()).isEqualTo(Instant.parse("2026-10-23T03:59:00Z"));

        PlanDraft.DraftTask words = draft.tasks().get(1);
        assertThat(words.basis()).isEqualTo(Basis.SUGGESTED); // its only requirement was not found
        assertThat(words.dueAt()).isEqualTo(DUE.toInstant()); // final day uses the real due time

        assertThat(draft.uncertainties()).contains("Topic is not specified.")
                .anyMatch(u -> u.contains("2 requirements could not be found"));
    }

    @Test
    void enforcesLimitsAndDropsEmptyOrDuplicateItems() {
        StringBuilder tasks = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            tasks.append("""
                    {"title":"Task %d","basis":"SUGGESTED","requirementIds":[],"suggestedDueDate":"",
                     "subtasks":[{"title":"a","basis":"x"},{"title":"A"},{"title":" "},{"title":"b"},{"title":"c"},
                                 {"title":"d"},{"title":"e"},{"title":"f"}]},""".formatted(i));
        }
        String output = "{\"tasks\":[{\"title\":\"  \"},{\"title\":\"Task 0\"}," + tasks + "{\"title\":\"x\"}]}";

        PlanDraft draft = validator.toDraft(output, ctx());

        assertThat(draft.tasks()).hasSize(5);
        assertThat(draft.tasks()).extracting(PlanDraft.DraftTask::title)
                .containsExactly("Task 0", "Task 1", "Task 2", "Task 3", "Task 4");
        assertThat(draft.tasks().get(1).subtasks()).extracting(PlanDraft.DraftSubtask::title)
                .containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    void dropsDatesOutsideTheAllowedRange() {
        String output = """
                {"tasks":[{"title":"Past","suggestedDueDate":"2026-10-01"},
                          {"title":"After due","suggestedDueDate":"2026-11-15"},
                          {"title":"Garbage","suggestedDueDate":"next tuesday"}]}
                """;

        PlanDraft draft = validator.toDraft(output, ctx());

        assertThat(draft.tasks()).allSatisfy(t -> assertThat(t.dueAt()).isNull());
    }

    @Test
    void rejectsMalformedOrEmptyOutput() {
        assertThatThrownBy(() -> validator.toDraft("{not json", ctx())).isInstanceOf(MalformedPlanException.class);
        assertThatThrownBy(() -> validator.toDraft("", ctx())).isInstanceOf(MalformedPlanException.class);
        assertThatThrownBy(() -> validator.toDraft("{\"tasks\":[]}", ctx()))
                .isInstanceOf(MalformedPlanException.class);
    }

    @Test
    void acceptsJsonWrappedInMarkdownFences() {
        PlanDraft draft = validator.toDraft("```json\n{\"tasks\":[{\"title\":\"Outline\"}]}\n```", ctx());

        assertThat(draft.tasks()).extracting(PlanDraft.DraftTask::title).containsExactly("Outline");
    }
}
