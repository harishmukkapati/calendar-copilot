package dev.calendarcopilot.task;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Creates a top-level task, or a subtask when parentId is set. */
public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        Long parentId,
        OffsetDateTime startAt,
        OffsetDateTime dueAt,
        @PositiveOrZero Integer sortOrder) {
}
