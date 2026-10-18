package dev.calendarcopilot.workspace;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    /** Tasks and events are removed by ON DELETE CASCADE in the database. */
    @Modifying
    @Query("delete from Workspace w where w.ownerId is null and w.lastSeenAt < :cutoff")
    int deleteGuestWorkspacesNotSeenSince(Instant cutoff);
}
