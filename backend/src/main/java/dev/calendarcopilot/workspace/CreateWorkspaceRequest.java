package dev.calendarcopilot.workspace;

import jakarta.validation.constraints.Size;

/** timeZone is the browser's IANA zone (e.g. America/New_York), used to place demo data. */
public record CreateWorkspaceRequest(@Size(max = 64) String timeZone) {
}
