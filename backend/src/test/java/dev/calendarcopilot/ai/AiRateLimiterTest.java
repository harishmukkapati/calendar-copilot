package dev.calendarcopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dev.calendarcopilot.common.ApiException;

class AiRateLimiterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final AiRateLimiter limiter = new AiRateLimiter(TestAi.props(), clock);

    @Test
    void limitsEachWorkspacePerHourAndRecoversAfterTheWindow() {
        UUID workspace = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            limiter.acquire(workspace, "1.1.1.1").close();
        }

        assertThatThrownBy(() -> limiter.acquire(workspace, "1.1.1.1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(3600L);
                });
        // Other workspaces are unaffected.
        limiter.acquire(UUID.randomUUID(), "1.1.1.1").close();

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        limiter.acquire(workspace, "1.1.1.1").close();
    }

    @Test
    void limitsEachIpAcrossWorkspaces() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire(UUID.randomUUID(), "2.2.2.2").close();
        }

        assertThatThrownBy(() -> limiter.acquire(UUID.randomUUID(), "2.2.2.2"))
                .hasMessageContaining("Too many AI requests");
    }

    @Test
    void capsConcurrentGenerations() {
        for (int i = 0; i < 4; i++) {
            limiter.acquire(UUID.randomUUID(), "ip-" + i); // held, not closed
        }

        assertThatThrownBy(() -> limiter.acquire(UUID.randomUUID(), "ip-x"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void enforcesLimitsExactlyUnderParallelRequests() throws Exception {
        // High concurrency cap so only the hourly limit is under test (the cap of 4 has its own test).
        AiProperties base = TestAi.props();
        AiRateLimiter limiter = new AiRateLimiter(new AiProperties(base.apiKey(), base.model(), base.thinkingLevel(),
                base.baseUrl(), base.timeout(), base.maxOutputTokens(), base.maxFiles(), base.maxFileSize(),
                base.maxPdfPages(), base.maxDocumentChars(), base.maxContextChars(), 100, base.limits()), clock);
        UUID workspace = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);

        try (java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                pool.submit(() -> {
                    start.await();
                    try (AiRateLimiter.Permit permit = limiter.acquire(workspace, "3.3.3.3")) {
                        allowed.incrementAndGet();
                    } catch (ApiException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(allowed.get()).isEqualTo(5); // per-workspace limit, never more
        assertThat(rejected.get()).isEqualTo(45);
    }
}
