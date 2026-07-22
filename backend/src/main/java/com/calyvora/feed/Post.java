package com.calyvora.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A post on the company feed. Visibility is stored on the row, not derived from the author's team,
 * so who can see a post never changes silently when someone moves department.
 */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PostKind kind = PostKind.UPDATE;

    @Column(nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PostVisibility visibility = PostVisibility.COMPANY;

    /** Set only when {@link #visibility} is {@code DEPARTMENT}. */
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Post() {
    }

    public Post(UUID id, UUID companyId, UUID authorId, PostKind kind, String body,
                PostVisibility visibility, UUID departmentId) {
        this.id = id;
        this.companyId = companyId;
        this.authorId = authorId;
        this.kind = kind;
        this.body = body;
        this.visibility = visibility;
        this.departmentId = departmentId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getAuthorId() { return authorId; }
    public PostKind getKind() { return kind; }
    public void setKind(PostKind kind) { this.kind = kind; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public PostVisibility getVisibility() { return visibility; }
    public void setVisibility(PostVisibility visibility) { this.visibility = visibility; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public Instant getCreatedAt() { return createdAt; }
}
