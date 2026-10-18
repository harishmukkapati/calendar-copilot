package dev.calendarcopilot.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A time-boxed calendar entry (meeting, class, workout). Unlike tasks, events have no hierarchy or status. */
@Entity
@Table(name = "calendar_event")
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 200)
    private String title;

    private String description;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false, length = 16)
    private String color;

    /** Shown as a bar without a time; stored as local midnight to 23:59 of the last day. */
    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: an UPDATE based on a stale read fails instead of overwriting newer data. */
    @Version
    private long version;

    protected CalendarEvent() {
    }

    public CalendarEvent(UUID workspaceId, String title, Instant startAt, Instant endAt, String color) {
        this.workspaceId = workspaceId;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.color = color;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getColor() { return color; }
    public boolean isAllDay() { return allDay; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public void setColor(String color) { this.color = color; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }
}
