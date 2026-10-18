package dev.calendarcopilot.event;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.calendarcopilot.common.InvalidRequestException;
import dev.calendarcopilot.common.NotFoundException;
import dev.calendarcopilot.workspace.WorkspaceService;

@Service
public class EventService {

    /** Longest allowed event; keeps a typo from drawing a year-long bar across the calendar. */
    static final Duration MAX_LENGTH = Duration.ofDays(62);

    private final EventRepository events;
    private final WorkspaceService workspaces;
    private final int maxEvents;

    public EventService(EventRepository events, WorkspaceService workspaces,
            @Value("${app.workspace.max-events}") int maxEvents) {
        this.events = events;
        this.workspaces = workspaces;
        this.maxEvents = maxEvents;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> list(UUID workspaceId) {
        return events.findByWorkspaceIdOrderByStartAtAsc(workspaceId);
    }

    @Transactional
    public CalendarEvent create(UUID workspaceId, CreateEventRequest request) {
        workspaces.requireExists(workspaceId);
        if (events.countByWorkspaceId(workspaceId) >= maxEvents) {
            throw new InvalidRequestException("This workspace has reached its limit of " + maxEvents + " events.");
        }
        Instant startAt = request.startAt().toInstant();
        Instant endAt = request.endAt().toInstant();
        requireEndAfterStart(startAt, endAt);

        String color = request.color() != null ? request.color() : EventColors.DEFAULT;
        CalendarEvent event = new CalendarEvent(workspaceId, request.title().strip(), startAt, endAt, color);
        event.setDescription(blankToNull(request.description()));
        event.setAllDay(Boolean.TRUE.equals(request.allDay()));
        return events.save(event);
    }

    @Transactional
    public CalendarEvent update(UUID workspaceId, Long id, UpdateEventRequest request) {
        CalendarEvent event = events.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Event not found."));
        if (request.title() != null) {
            event.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            event.setDescription(blankToNull(request.description()));
        }
        if (request.startAt() != null) {
            event.setStartAt(request.startAt().toInstant());
        }
        if (request.endAt() != null) {
            event.setEndAt(request.endAt().toInstant());
        }
        if (request.color() != null) {
            event.setColor(request.color());
        }
        if (request.allDay() != null) {
            event.setAllDay(request.allDay());
        }
        requireEndAfterStart(event.getStartAt(), event.getEndAt());
        return event;
    }

    @Transactional
    public void delete(UUID workspaceId, Long id) {
        if (events.deleteByIdAndWorkspaceId(id, workspaceId) == 0) {
            throw new NotFoundException("Event not found.");
        }
    }

    private static void requireEndAfterStart(Instant startAt, Instant endAt) {
        if (endAt.isBefore(startAt)) {
            throw new InvalidRequestException("End time must be after the start time.");
        }
        if (Duration.between(startAt, endAt).compareTo(MAX_LENGTH) > 0) {
            throw new InvalidRequestException("Events can be at most " + MAX_LENGTH.toDays() + " days long.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
