package dev.calendarcopilot.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.calendarcopilot.common.ApiException;

/**
 * Protects the Gemini budget and the small server: sliding-window limits per workspace,
 * per client IP and globally per day, plus a cap on concurrent generations.
 * In-memory is enough for a single instance; multiple instances would need Redis or similar.
 */
@Component
public class AiRateLimiter {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final AiProperties.Limits limits;
    private final Clock clock;
    private final Semaphore inFlight;
    private final Map<String, Deque<Instant>> windows = new HashMap<>();

    public AiRateLimiter(AiProperties props, Clock clock) {
        this.limits = props.limits();
        this.clock = clock;
        this.inFlight = new Semaphore(props.maxConcurrent());
    }

    /** Release with try-with-resources once the generation finishes. */
    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    public Permit acquire(UUID workspaceId, String clientIp) {
        Instant now = clock.instant();
        synchronized (windows) {
            check("ws:" + workspaceId, limits.perWorkspacePerHour(), HOUR, now,
                    "You've reached the limit of " + limits.perWorkspacePerHour() + " AI plans per hour.");
            check("ip:" + clientIp, limits.perIpPerHour(), HOUR, now,
                    "Too many AI requests from your network. Please try again later.");
            check("global", limits.globalPerDay(), DAY, now,
                    "The demo has reached today's AI limit. Manual task creation still works.");
            if (!inFlight.tryAcquire()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The AI planner is busy. Please try again in a few seconds.", "busy", 5L);
            }
            // Count the attempt only once every check passed.
            record("ws:" + workspaceId, now);
            record("ip:" + clientIp, now);
            record("global", now);
        }
        return inFlight::release;
    }

    private void check(String key, int limit, Duration window, Instant now, String message) {
        Deque<Instant> hits = windows.get(key);
        if (hits == null) {
            return;
        }
        prune(hits, window, now);
        if (hits.size() >= limit) {
            long retryAfter = Math.max(1, Duration.between(now, hits.peekFirst().plus(window)).toSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message, "rate_limited_" + key.split(":")[0],
                    retryAfter);
        }
    }

    private void record(String key, Instant now) {
        windows.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(now);
    }

    private static void prune(Deque<Instant> hits, Duration window, Instant now) {
        Instant cutoff = now.minus(window);
        while (!hits.isEmpty() && hits.peekFirst().isBefore(cutoff)) {
            hits.removeFirst();
        }
    }

    /** Drops idle keys so memory does not grow with every visitor. */
    @Scheduled(fixedRate = 600_000)
    void evictIdle() {
        Instant now = clock.instant();
        synchronized (windows) {
            windows.entrySet().removeIf(e -> {
                prune(e.getValue(), e.getKey().equals("global") ? DAY : HOUR, now);
                return e.getValue().isEmpty();
            });
        }
    }
}
