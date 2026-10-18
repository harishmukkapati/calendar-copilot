package dev.calendarcopilot.task;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.calendarcopilot.common.WorkspaceHeader;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** Returns every task in the workspace as a flat list; the client builds the tree from parentId. */
    @GetMapping
    public List<TaskResponse> list(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId) {
        return service.list(workspaceId).stream().map(TaskResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId,
            @Valid @RequestBody CreateTaskRequest request) {
        return TaskResponse.from(service.create(workspaceId, request, TaskOrigin.MANUAL));
    }

    @PatchMapping("/{id}")
    public TaskResponse update(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId, @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return TaskResponse.from(service.update(workspaceId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId, @PathVariable Long id) {
        service.delete(workspaceId, id);
    }
}
