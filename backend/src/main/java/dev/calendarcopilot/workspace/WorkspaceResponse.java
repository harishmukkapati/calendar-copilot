package dev.calendarcopilot.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(UUID id, String timeZone, Instant createdAt) {

    static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(w.getId(), w.getTimeZone(), w.getCreatedAt());
    }
}
