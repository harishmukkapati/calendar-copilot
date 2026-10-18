package dev.calendarcopilot.workspace;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.calendarcopilot.common.WorkspaceHeader;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@Valid @RequestBody(required = false) CreateWorkspaceRequest request) {
        String timeZone = request == null ? null : request.timeZone();
        return WorkspaceResponse.from(service.create(timeZone));
    }

    /** Called on app start: confirms the stored workspace still exists (404 means create a new one). */
    @GetMapping("/current")
    public WorkspaceResponse current(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId) {
        return WorkspaceResponse.from(service.touch(workspaceId));
    }
}
