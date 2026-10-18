package dev.calendarcopilot.ai;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.calendarcopilot.ai.DocumentExtractor.UploadedFile;
import dev.calendarcopilot.common.ClientIp;
import dev.calendarcopilot.common.WorkspaceHeader;
import dev.calendarcopilot.task.CreatePlanRequest;
import dev.calendarcopilot.task.TaskResponse;
import dev.calendarcopilot.task.TaskService;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiPlanService planService;
    private final TaskService taskService;

    public AiController(AiPlanService planService, TaskService taskService) {
        this.planService = planService;
        this.taskService = taskService;
    }

    /** Returns a draft plan for review. Nothing is saved. Uploaded files are discarded after the request. */
    @PostMapping(path = "/generate-plan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlanDraft generatePlan(
            @RequestHeader(WorkspaceHeader.NAME) UUID workspaceId,
            @RequestParam String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dueAt,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false) List<MultipartFile> instructions,
            @RequestParam(required = false) List<MultipartFile> rubric,
            @RequestParam(required = false) List<MultipartFile> supporting,
            HttpServletRequest request) {
        List<UploadedFile> files = new ArrayList<>();
        add(files, DocumentCategory.INSTRUCTIONS, instructions);
        add(files, DocumentCategory.RUBRIC, rubric);
        add(files, DocumentCategory.SUPPORTING, supporting);
        return planService.generate(workspaceId, new AiPlanService.PlanRequest(title, dueAt, context, timeZone),
                files, ClientIp.of(request));
    }

    /** Saves the plan the user reviewed and confirmed. */
    @PostMapping("/confirm-plan")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TaskResponse> confirmPlan(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId,
            @Valid @RequestBody CreatePlanRequest request) {
        return taskService.createPlan(workspaceId, request).stream().map(TaskResponse::from).toList();
    }

    private static void add(List<UploadedFile> target, DocumentCategory category, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            // Browsers send an empty, unnamed part for an unused file input; skip those.
            boolean unnamed = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank();
            if (!(file.isEmpty() && unnamed)) {
                target.add(new UploadedFile(category, file));
            }
        }
    }
}
