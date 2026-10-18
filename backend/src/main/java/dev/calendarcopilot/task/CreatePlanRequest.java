package dev.calendarcopilot.task;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * A reviewed (and possibly edited) AI plan to save. Either attaches the tasks under an
 * existing task (parentTaskId) or creates a new top-level task from title/description/dueAt.
 * Limits are a little looser than the AI's 5x5 so users can add items while reviewing.
 */
public record CreatePlanRequest(
        Long parentTaskId,
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        OffsetDateTime dueAt,
        @NotEmpty @Size(max = 10) List<@Valid PlanTask> tasks) {

    public record PlanTask(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            OffsetDateTime dueAt,
            @Size(max = 10) List<@Valid PlanSubtask> subtasks) {
    }

    public record PlanSubtask(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description) {
    }
}
