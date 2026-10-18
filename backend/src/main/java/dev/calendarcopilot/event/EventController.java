package dev.calendarcopilot.event;

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
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventResponse> list(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId) {
        return service.list(workspaceId).stream().map(EventResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId,
            @Valid @RequestBody CreateEventRequest request) {
        return EventResponse.from(service.create(workspaceId, request));
    }

    @PatchMapping("/{id}")
    public EventResponse update(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId, @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request) {
        return EventResponse.from(service.update(workspaceId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(WorkspaceHeader.NAME) UUID workspaceId, @PathVariable Long id) {
        service.delete(workspaceId, id);
    }
}
