package dev.calendarcopilot.task;

import java.time.Instant;

public record TaskResponse(
        Long id,
        Long parentId,
        String title,
        String description,
        TaskStatus status,
        TaskOrigin origin,
        Instant startAt,
        Instant dueAt,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(), t.getParentId(), t.getTitle(), t.getDescription(), t.getStatus(),
                t.getOrigin(), t.getStartAt(), t.getDueAt(), t.getSortOrder(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
