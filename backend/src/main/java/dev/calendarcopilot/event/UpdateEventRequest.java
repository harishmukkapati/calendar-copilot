package dev.calendarcopilot.event;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: null fields are left unchanged. */
public record UpdateEventRequest(
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        @Size(max = 5000) String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        @Pattern(regexp = EventColors.PATTERN) String color,
        Boolean allDay) {
}
