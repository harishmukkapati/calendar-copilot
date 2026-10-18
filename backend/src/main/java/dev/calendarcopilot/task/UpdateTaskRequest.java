package dev.calendarcopilot.task;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged. Because null means "unchanged",
 * removing a date is requested explicitly with clearStartAt / clearDueAt.
 */
public record UpdateTaskRequest(
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        @Size(max = 5000) String description,
        TaskStatus status,
        OffsetDateTime startAt,
        OffsetDateTime dueAt,
        @PositiveOrZero Integer sortOrder,
        Boolean clearStartAt,
        Boolean clearDueAt) {
}
