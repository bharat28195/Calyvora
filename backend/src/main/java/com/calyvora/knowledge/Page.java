package com.calyvora.knowledge;

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
 * A Markdown document inside a {@link Space} (Knowledge OS slices K2/K3). The cross-app links are
 * {@code authorId} (a People OS employee) and {@code linkedTaskId} (a Work OS task) — the
 * task&#8596;doc&#8596;person knowledge graph. Anemic entity; rules live in {@link PageService}.
 */
@Entity
@Table(name = "pages")
public class Page {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PageStatus status = PageStatus.DRAFT;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "linked_task_id")
    private UUID linkedTaskId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Page() {
    }

    public Page(UUID id, UUID companyId, UUID spaceId, String title, UUID authorId, UUID createdBy) {
        this.id = id;
        this.companyId = companyId;
        this.spaceId = spaceId;
        this.title = title;
        this.authorId = authorId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public PageStatus getStatus() {
        return status;
    }

    public void setStatus(PageStatus status) {
        this.status = status;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public void setAuthorId(UUID authorId) {
        this.authorId = authorId;
    }

    public UUID getLinkedTaskId() {
        return linkedTaskId;
    }

    public void setLinkedTaskId(UUID linkedTaskId) {
        this.linkedTaskId = linkedTaskId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
