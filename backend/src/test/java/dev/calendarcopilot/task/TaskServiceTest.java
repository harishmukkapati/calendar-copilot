package dev.calendarcopilot.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.calendarcopilot.common.InvalidRequestException;
import dev.calendarcopilot.common.NotFoundException;
import dev.calendarcopilot.workspace.WorkspaceService;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID WORKSPACE = UUID.randomUUID();

    @Mock
    private TaskRepository tasks;
    @Mock
    private WorkspaceService workspaces;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, workspaces, 10, 3);
    }

    @Test
    void rejectsNewTaskWhenWorkspaceIsFull() {
        when(tasks.countByWorkspaceId(WORKSPACE)).thenReturn(10L);

        assertThatThrownBy(() -> service.create(WORKSPACE, request("One too many", null), TaskOrigin.MANUAL))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("limit of 10 tasks");
        verify(tasks, never()).save(any());
    }

    @Test
    void rejectsParentFromAnotherWorkspace() {
        when(tasks.findByIdAndWorkspaceId(42L, WORKSPACE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(WORKSPACE, request("Child", 42L), TaskOrigin.MANUAL))
                .isInstanceOf(NotFoundException.class);
        verify(tasks, never()).save(any());
    }

    @Test
    void appendsSubtaskAfterExistingSiblings() {
        Task parent = new Task(WORKSPACE, null, "Parent", TaskOrigin.MANUAL);
        when(tasks.findByIdAndWorkspaceId(7L, WORKSPACE)).thenReturn(Optional.of(parent));
        when(tasks.maxChildSortOrder(7L)).thenReturn(2);
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task created = service.create(WORKSPACE, request("  Third child ", 7L), TaskOrigin.AI);

        assertThat(created.getSortOrder()).isEqualTo(3);
        assertThat(created.getTitle()).isEqualTo("Third child");
        assertThat(created.getParentId()).isEqualTo(7L);
        assertThat(created.getOrigin()).isEqualTo(TaskOrigin.AI);
        assertThat(created.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void rejectsUpdateThatPutsStartAfterDue() {
        Task task = new Task(WORKSPACE, null, "Task", TaskOrigin.MANUAL);
        task.setDueAt(OffsetDateTime.parse("2026-11-01T10:00:00Z").toInstant());
        when(tasks.findByIdAndWorkspaceId(1L, WORKSPACE)).thenReturn(Optional.of(task));

        UpdateTaskRequest update = new UpdateTaskRequest(null, null, null,
                OffsetDateTime.parse("2026-11-05T10:00:00Z"), null, null, null, null);

        assertThatThrownBy(() -> service.update(WORKSPACE, 1L, update))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static CreateTaskRequest request(String title, Long parentId) {
        return new CreateTaskRequest(title, null, parentId, null, null, null);
    }
}
