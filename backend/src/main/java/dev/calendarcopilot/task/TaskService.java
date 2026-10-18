package dev.calendarcopilot.task;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.calendarcopilot.common.InvalidRequestException;
import dev.calendarcopilot.common.NotFoundException;
import dev.calendarcopilot.workspace.WorkspaceService;

@Service
public class TaskService {

    private final TaskRepository tasks;
    private final WorkspaceService workspaces;
    private final int maxTasks;
    private final int maxDepth;

    public TaskService(TaskRepository tasks, WorkspaceService workspaces,
            @Value("${app.workspace.max-tasks}") int maxTasks,
            @Value("${app.task.max-depth}") int maxDepth) {
        this.tasks = tasks;
        this.workspaces = workspaces;
        this.maxTasks = maxTasks;
        this.maxDepth = maxDepth;
    }

    @Transactional(readOnly = true)
    public List<Task> list(UUID workspaceId) {
        return tasks.findByWorkspaceIdOrderBySortOrderAscIdAsc(workspaceId);
    }

    @Transactional
    public Task create(UUID workspaceId, CreateTaskRequest request, TaskOrigin origin) {
        workspaces.requireExists(workspaceId);
        if (tasks.countByWorkspaceId(workspaceId) >= maxTasks) {
            throw new InvalidRequestException("This workspace has reached its limit of " + maxTasks + " tasks.");
        }

        Long parentId = request.parentId();
        if (parentId != null) {
            Task parent = find(workspaceId, parentId, "Parent task not found.");
            if (depthOf(parent) + 1 > maxDepth) {
                throw new InvalidRequestException("Subtasks can be nested at most " + maxDepth + " levels deep.");
            }
        }

        Instant startAt = toInstant(request.startAt());
        Instant dueAt = toInstant(request.dueAt());
        requireStartBeforeDue(startAt, dueAt);

        Task task = new Task(workspaceId, parentId, request.title().strip(), origin);
        task.setDescription(blankToNull(request.description()));
        task.setStartAt(startAt);
        task.setDueAt(dueAt);
        task.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextSortOrder(workspaceId, parentId));
        return tasks.save(task);
    }

    @Transactional
    public Task update(UUID workspaceId, Long id, UpdateTaskRequest request) {
        Task task = find(workspaceId, id, "Task not found.");

        if (request.title() != null) {
            task.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            task.setDescription(blankToNull(request.description()));
        }
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        if (request.sortOrder() != null) {
            task.setSortOrder(request.sortOrder());
        }
        if (Boolean.TRUE.equals(request.clearStartAt())) {
            task.setStartAt(null);
        } else if (request.startAt() != null) {
            task.setStartAt(request.startAt().toInstant());
        }
        if (Boolean.TRUE.equals(request.clearDueAt())) {
            task.setDueAt(null);
        } else if (request.dueAt() != null) {
            task.setDueAt(request.dueAt().toInstant());
        }
        requireStartBeforeDue(task.getStartAt(), task.getDueAt());
        return task;
    }

    /** Deletes the task and, through the database cascade, all of its subtasks. */
    @Transactional
    public void delete(UUID workspaceId, Long id) {
        if (tasks.deleteByIdAndWorkspaceId(id, workspaceId) == 0) {
            throw new NotFoundException("Task not found.");
        }
    }

    private Task find(UUID workspaceId, Long id, String notFoundMessage) {
        return tasks.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException(notFoundMessage));
    }

    /** Depth of a task counting from 1 at the root. Bounded by maxDepth, so at most a few lookups. */
    private int depthOf(Task task) {
        int depth = 1;
        Long parentId = task.getParentId();
        while (parentId != null) {
            depth++;
            parentId = tasks.findById(parentId).map(Task::getParentId).orElse(null);
        }
        return depth;
    }

    private int nextSortOrder(UUID workspaceId, Long parentId) {
        int max = parentId == null ? tasks.maxRootSortOrder(workspaceId) : tasks.maxChildSortOrder(parentId);
        return max + 1;
    }

    private static void requireStartBeforeDue(Instant startAt, Instant dueAt) {
        if (startAt != null && dueAt != null && startAt.isAfter(dueAt)) {
            throw new InvalidRequestException("Start time must be before the due time.");
        }
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
