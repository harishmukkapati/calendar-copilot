package dev.calendarcopilot.ai;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import dev.calendarcopilot.ai.DocumentExtractor.UploadedFile;
import dev.calendarcopilot.ai.PlanDraft.Verification;
import dev.calendarcopilot.ai.PlanValidator.MalformedPlanException;
import dev.calendarcopilot.common.ApiException;
import dev.calendarcopilot.common.InvalidRequestException;
import dev.calendarcopilot.workspace.WorkspaceService;

/**
 * Generate flow: validate input -> extract documents -> rate limit -> Gemini (structured
 * JSON) -> validate + verify citations -> draft for user review. Retries once on
 * malformed output. Logs metadata only, never document or prompt content.
 */
@Service
public class AiPlanService {

    private static final Logger log = LoggerFactory.getLogger(AiPlanService.class);
    private static final int MAX_ATTEMPTS = 2;

    public record PlanRequest(String title, OffsetDateTime dueAt, String context, String timeZone) {
    }

    private final AiProperties props;
    private final WorkspaceService workspaces;
    private final DocumentExtractor extractor;
    private final AiRateLimiter limiter;
    private final GeminiClient gemini;
    private final PlanValidator validator;
    private final Clock clock;

    public AiPlanService(AiProperties props, WorkspaceService workspaces, DocumentExtractor extractor,
            AiRateLimiter limiter, GeminiClient gemini, PlanValidator validator, Clock clock) {
        this.props = props;
        this.workspaces = workspaces;
        this.extractor = extractor;
        this.limiter = limiter;
        this.gemini = gemini;
        this.validator = validator;
        this.clock = clock;
    }

    public PlanDraft generate(UUID workspaceId, PlanRequest request, List<UploadedFile> files, String clientIp) {
        if (!props.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI planning is not configured on this server.",
                    "not_configured");
        }
        workspaces.requireExists(workspaceId);
        String title = request.title() == null ? "" : request.title().strip();
        if (title.isEmpty() || title.length() > 200) {
            throw new InvalidRequestException("Enter a task title (up to 200 characters) before generating a plan.");
        }
        if (request.context() != null && request.context().length() > props.maxContextChars()) {
            throw new InvalidRequestException("Additional context must be at most " + props.maxContextChars()
                    + " characters.");
        }
        ZoneId zone = zone(request);
        LocalDate today = LocalDate.now(clock.withZone(zone));

        // Bad uploads are rejected before they count against the user's AI quota.
        List<SourceDocument> documents = extractor.extract(files);
        long started = System.nanoTime();
        try (AiRateLimiter.Permit permit = limiter.acquire(workspaceId, clientIp)) {
            List<GeminiClient.Part> parts = PlanPrompt.userParts(title, request.dueAt(), request.context(), today,
                    documents);
            PlanValidator.Context ctx = new PlanValidator.Context(documents, request.dueAt(), today, zone,
                    props.model());
            for (int attempt = 1; ; attempt++) {
                String output = gemini.generateJson(PlanPrompt.SYSTEM_INSTRUCTION, parts, PlanPrompt.responseSchema());
                try {
                    PlanDraft draft = validator.toDraft(output, ctx);
                    logSuccess(workspaceId, documents, draft, started, attempt);
                    return draft;
                } catch (MalformedPlanException e) {
                    log.warn("ai.generation.retry workspace={} attempt={} cause={}", workspaceId, attempt,
                            e.getMessage());
                    if (attempt >= MAX_ATTEMPTS) {
                        throw new ApiException(HttpStatus.BAD_GATEWAY,
                                "The AI returned a plan we couldn't use. Please try again.", "malformed_output");
                    }
                }
            }
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                log.info("ai.generation.rejected workspace={} reason={}", workspaceId, e.getReason());
            } else {
                log.warn("ai.generation.failed workspace={} reason={} files={} latencyMs={}", workspaceId,
                        e.getReason(), describe(documents), elapsedMs(started));
            }
            throw e;
        }
    }

    private void logSuccess(UUID workspaceId, List<SourceDocument> documents, PlanDraft draft, long started,
            int attempts) {
        Map<Verification, Long> verification = draft.requirements().stream()
                .collect(Collectors.groupingBy(PlanDraft.Requirement::verification, Collectors.counting()));
        int subtasks = draft.tasks().stream().mapToInt(t -> t.subtasks().size()).sum();
        log.info("ai.generation.ok workspace={} files={} tasks={} subtasks={} requirements={} attempts={} latencyMs={}",
                workspaceId, describe(documents), draft.tasks().size(), subtasks, verification, attempts,
                elapsedMs(started));
    }

    /** e.g. [PDF_TEXT:12034ch, IMAGE] — types and sizes only, no names or content. */
    private static String describe(List<SourceDocument> documents) {
        return documents.stream()
                .map(d -> d.hasText() ? d.kind() + ":" + d.text().length() + "ch" : d.kind().toString())
                .toList().toString();
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static ZoneId zone(PlanRequest request) {
        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            try {
                return ZoneId.of(request.timeZone());
            } catch (DateTimeException e) {
                throw new InvalidRequestException("Unknown time zone: " + request.timeZone());
            }
        }
        return request.dueAt() != null ? request.dueAt().getOffset() : ZoneId.of("UTC");
    }
}
