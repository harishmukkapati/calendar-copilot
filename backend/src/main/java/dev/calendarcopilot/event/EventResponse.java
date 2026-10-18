package dev.calendarcopilot.event;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        String color,
        boolean allDay,
        Instant createdAt,
        Instant updatedAt) {

    static EventResponse from(CalendarEvent e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getStartAt(), e.getEndAt(),
                e.getColor(), e.isAllDay(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
