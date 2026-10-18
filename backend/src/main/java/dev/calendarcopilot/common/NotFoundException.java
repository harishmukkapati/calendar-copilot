package dev.calendarcopilot.common;

/** Thrown when a resource does not exist or belongs to another workspace (mapped to 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
