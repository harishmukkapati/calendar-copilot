package dev.calendarcopilot.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByWorkspaceIdOrderBySortOrderAscIdAsc(UUID workspaceId);

    /** Scoping every lookup by workspace is what keeps guests out of each other's data. */
    Optional<Task> findByIdAndWorkspaceId(Long id, UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    @Query("select coalesce(max(t.sortOrder), -1) from Task t where t.workspaceId = :workspaceId and t.parentId is null")
    int maxRootSortOrder(UUID workspaceId);

    @Query("select coalesce(max(t.sortOrder), -1) from Task t where t.parentId = :parentId")
    int maxChildSortOrder(Long parentId);

    /** Subtasks are removed by ON DELETE CASCADE on parent_id. */
    @Modifying
    @Query("delete from Task t where t.id = :id and t.workspaceId = :workspaceId")
    int deleteByIdAndWorkspaceId(Long id, UUID workspaceId);
}
