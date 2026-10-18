package dev.calendarcopilot.event;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @Pattern(regexp = EventColors.PATTERN) String color,
        Boolean allDay) {
}
