package dev.calendarcopilot.workspace;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.calendarcopilot.common.InvalidRequestException;
import dev.calendarcopilot.common.NotFoundException;

@Service
public class WorkspaceService {

    static final String DEFAULT_TIME_ZONE = "America/New_York";
    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaces;
    private final DemoDataSeeder seeder;
    private final Clock clock;
    private final Duration guestTtl;

    public WorkspaceService(WorkspaceRepository workspaces, DemoDataSeeder seeder, Clock clock,
            @Value("${app.workspace.ttl-days}") int ttlDays) {
        this.workspaces = workspaces;
        this.seeder = seeder;
        this.clock = clock;
        this.guestTtl = Duration.ofDays(ttlDays);
    }

    /** Creates an isolated guest workspace pre-filled with demo data. */
    @Transactional
    public Workspace create(String timeZone) {
        ZoneId zone = parseZone(timeZone);
        Workspace workspace = workspaces.save(new Workspace(UUID.randomUUID(), zone.getId(), clock.instant()));
        seeder.seed(workspace.getId(), zone);
        return workspace;
    }

    /** Marks the workspace as active so it is not cleaned up. */
    @Transactional
    public Workspace touch(UUID id) {
        Workspace workspace = workspaces.findById(id)
                .orElseThrow(() -> new NotFoundException("Workspace not found."));
        workspace.setLastSeenAt(clock.instant());
        return workspace;
    }

    public void requireExists(UUID id) {
        if (!workspaces.existsById(id)) {
            throw new NotFoundException("Workspace not found.");
        }
    }

    /** Nightly cleanup so public demo data does not accumulate. */
    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    @Transactional
    public int deleteExpiredGuestWorkspaces() {
        Instant cutoff = clock.instant().minus(guestTtl);
        int deleted = workspaces.deleteGuestWorkspacesNotSeenSince(cutoff);
        log.info("Deleted {} expired guest workspaces", deleted);
        return deleted;
    }

    private static ZoneId parseZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of(DEFAULT_TIME_ZONE);
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new InvalidRequestException("Unknown time zone: " + timeZone);
        }
    }
}
