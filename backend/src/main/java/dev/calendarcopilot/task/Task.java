package dev.calendarcopilot.task;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A task or subtask. The hierarchy is a plain parentId column rather than a JPA
 * relationship: the API returns a flat list and the client builds the tree, which
 * keeps every read to one query with no lazy loading.
 */
@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "parent_id", updatable = false)
    private Long parentId;

    @Column(nullable = false, length = 200)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TaskOrigin origin = TaskOrigin.MANUAL;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
    }

    public Task(UUID workspaceId, Long parentId, String title, TaskOrigin origin) {
        this.workspaceId = workspaceId;
        this.parentId = parentId;
        this.title = title;
        this.origin = origin;
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
    public Long getParentId() { return parentId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskOrigin getOrigin() { return origin; }
    public Instant getStartAt() { return startAt; }
    public Instant getDueAt() { return dueAt; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
