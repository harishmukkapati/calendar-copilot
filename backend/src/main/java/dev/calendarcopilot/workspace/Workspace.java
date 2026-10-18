package dev.calendarcopilot.workspace;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workspace")
public class Workspace {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Workspace() {
    }

    public Workspace(UUID id, String timeZone, Instant now) {
        this.id = id;
        this.timeZone = timeZone;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getTimeZone() { return timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }

    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
