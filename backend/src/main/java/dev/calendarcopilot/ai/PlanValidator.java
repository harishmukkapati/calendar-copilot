package dev.calendarcopilot.ai;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.calendarcopilot.ai.PlanDraft.Basis;
import dev.calendarcopilot.ai.PlanDraft.DocumentSummary;
import dev.calendarcopilot.ai.PlanDraft.DraftSubtask;
import dev.calendarcopilot.ai.PlanDraft.DraftTask;
import dev.calendarcopilot.ai.PlanDraft.Requirement;
import dev.calendarcopilot.ai.PlanDraft.Verification;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the model's raw JSON into a {@link PlanDraft} the UI can trust structurally:
 * enforces size limits, drops empty or duplicate items, removes references to unknown
 * requirements or documents, verifies citations, keeps dates inside the allowed range,
 * and downgrades "SOURCE" claims that have no verified support.
 */
@Component
public class PlanValidator {

    /** Thrown when the output is unusable; the service retries once. */
    static class MalformedPlanException extends RuntimeException {
        MalformedPlanException(String message) {
            super(message);
        }
    }

    // Raw model output. Every field may be missing or wrong, so everything is checked.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawPlan(String summary, List<RawRequirement> requirements, List<RawTask> tasks, List<String> uncertainties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawRequirement(String id, String text, String documentId, String quote) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawTask(String title, String description, String basis, List<String> requirementIds,
            String suggestedDueDate, List<RawSubtask> subtasks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawSubtask(String title, String basis, List<String> requirementIds) {
    }

    /** Context the validator needs besides the model output. */
    record Context(List<SourceDocument> documents, OffsetDateTime finalDueAt, LocalDate today, ZoneId zone,
            String model) {
    }

    private final JsonMapper json;
    private final CitationVerifier verifier;

    public PlanValidator(JsonMapper json, CitationVerifier verifier) {
        this.json = json;
        this.verifier = verifier;
    }

    public PlanDraft toDraft(String modelOutput, Context ctx) {
        RawPlan raw = parse(modelOutput);
        Map<String, SourceDocument> docsById = ctx.documents().stream()
                .collect(Collectors.toMap(SourceDocument::id, Function.identity()));

        Map<String, Requirement> requirements = requirements(raw, docsById);
        Set<String> supported = requirements.values().stream()
                .filter(r -> r.verification() != Verification.NOT_FOUND)
                .map(Requirement::id)
                .collect(Collectors.toSet());

        List<DraftTask> tasks = tasks(raw, requirements.keySet(), supported, ctx);
        if (tasks.isEmpty()) {
            throw new MalformedPlanException("no usable tasks");
        }

        List<String> uncertainties = new ArrayList<>(cleanList(raw.uncertainties(), 6, 300));
        long unverified = requirements.values().stream()
                .filter(r -> r.verification() == Verification.NOT_FOUND).count();
        if (unverified > 0) {
            uncertainties.add(unverified + (unverified == 1 ? " requirement" : " requirements")
                    + " could not be found word-for-word in your documents. Check them before relying on them.");
        }
        if (ctx.documents().stream().anyMatch(SourceDocument::truncated)) {
            uncertainties.add("Some documents were too long, so only their first part was read.");
        }

        return new PlanDraft(
                truncate(blankToNull(raw.summary()), 500),
                List.copyOf(requirements.values()),
                tasks,
                uncertainties,
                ctx.documents().stream().map(DocumentSummary::of).toList(),
                ctx.model());
    }

    private RawPlan parse(String output) {
        if (output == null || output.isBlank()) {
            throw new MalformedPlanException("empty output");
        }
        String text = output.strip();
        // Defensive: structured output should not include Markdown fences, but strip them if present.
        if (text.startsWith("```")) {
            text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        try {
            RawPlan plan = json.readValue(text, RawPlan.class);
            if (plan == null) {
                throw new MalformedPlanException("null plan");
            }
            return plan;
        } catch (JacksonException e) {
            throw new MalformedPlanException("invalid JSON: " + e.getOriginalMessage());
        }
    }

    private Map<String, Requirement> requirements(RawPlan raw, Map<String, SourceDocument> docsById) {
        Map<String, Requirement> result = new LinkedHashMap<>();
        if (raw.requirements() == null) {
            return result;
        }
        for (RawRequirement r : raw.requirements()) {
            if (result.size() >= PlanPrompt.MAX_REQUIREMENTS || r == null) {
                break;
            }
            String id = blankToNull(r.id());
            String text = blankToNull(r.text());
            if (id == null || text == null || result.containsKey(id)) {
                continue;
            }
            SourceDocument doc = r.documentId() == null ? null : docsById.get(r.documentId().strip());
            String quote = truncate(blankToNull(r.quote()), 300);
            Verification verification = verifier.verify(quote, doc);
            result.put(id, new Requirement(id, truncate(text, 300), doc == null ? null : doc.id(),
                    doc == null ? null : doc.name(), quote, verification));
        }
        return result;
    }

    private List<DraftTask> tasks(RawPlan raw, Set<String> knownRequirements, Set<String> supported, Context ctx) {
        List<DraftTask> result = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();
        for (RawTask t : raw.tasks() == null ? List.<RawTask>of() : raw.tasks()) {
            if (result.size() >= PlanPrompt.MAX_TASKS || t == null) {
                break;
            }
            String title = truncate(blankToNull(t.title()), 200);
            if (title == null || !seenTitles.add(title.toLowerCase(Locale.ROOT))) {
                continue;
            }
            List<String> reqIds = keepKnown(t.requirementIds(), knownRequirements);
            result.add(new DraftTask(
                    title,
                    truncate(blankToNull(t.description()), 1000),
                    basis(t.basis(), reqIds, supported),
                    reqIds,
                    dueAt(t.suggestedDueDate(), ctx),
                    subtasks(t.subtasks(), knownRequirements, supported)));
        }
        return result;
    }

    private List<DraftSubtask> subtasks(List<RawSubtask> raw, Set<String> knownRequirements, Set<String> supported) {
        List<DraftSubtask> result = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();
        for (RawSubtask s : raw == null ? List.<RawSubtask>of() : raw) {
            if (result.size() >= PlanPrompt.MAX_SUBTASKS || s == null) {
                break;
            }
            String title = truncate(blankToNull(s.title()), 200);
            if (title == null || !seenTitles.add(title.toLowerCase(Locale.ROOT))) {
                continue;
            }
            List<String> reqIds = keepKnown(s.requirementIds(), knownRequirements);
            result.add(new DraftSubtask(title, basis(s.basis(), reqIds, supported), reqIds));
        }
        return result;
    }

    /** "SOURCE" only survives if at least one linked requirement was not disproven. */
    private static Basis basis(String claimed, List<String> requirementIds, Set<String> supported) {
        boolean claimsSource = "SOURCE".equalsIgnoreCase(claimed == null ? "" : claimed.strip());
        boolean hasSupport = requirementIds.stream().anyMatch(supported::contains);
        return claimsSource && hasSupport ? Basis.SOURCE : Basis.SUGGESTED;
    }

    /** Keeps suggested dates between today and the final due date; the last day uses the real due time. */
    private static Instant dueAt(String suggested, Context ctx) {
        String value = blankToNull(suggested);
        if (value == null) {
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
        if (date.isBefore(ctx.today())) {
            return null;
        }
        if (ctx.finalDueAt() != null) {
            LocalDate finalDate = ctx.finalDueAt().atZoneSameInstant(ctx.zone()).toLocalDate();
            if (date.isAfter(finalDate)) {
                return null;
            }
            if (date.equals(finalDate)) {
                return ctx.finalDueAt().toInstant();
            }
        }
        return date.atTime(LocalTime.of(23, 59)).atZone(ctx.zone()).toInstant();
    }

    private static List<String> keepKnown(List<String> ids, Set<String> known) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(id -> id != null && known.contains(id.strip())).map(String::strip).distinct()
                .toList();
    }

    private static List<String> cleanList(List<String> values, int max, int maxLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(PlanValidator::blankToNull).filter(v -> v != null)
                .map(v -> truncate(v, maxLength)).distinct().limit(max).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1).strip() + "…";
    }
}
