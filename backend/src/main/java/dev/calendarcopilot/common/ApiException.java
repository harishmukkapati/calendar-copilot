package dev.calendarcopilot.common;

import org.springframework.http.HttpStatus;

/**
 * An error with a specific HTTP status and a message that is safe to show users.
 * {@code reason} is a short machine-readable code for logs and metrics only.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String userMessage, String reason) {
        this(status, userMessage, reason, null);
    }

    public ApiException(HttpStatus status, String userMessage, String reason, Long retryAfterSeconds) {
        super(userMessage);
        this.status = status;
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Long getRetryAfterSeconds() { return retryAfterSeconds; }
}
