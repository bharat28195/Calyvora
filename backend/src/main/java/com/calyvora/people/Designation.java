package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A rung on the ladder this particular company uses — Intern, Junior Developer, Lead, Principal.
 *
 * <p>Customer-defined, because no two of them agree: an agency has three levels and a product company
 * has nine, and shipping our own list would mean a code change per sale.
 *
 * <p><b>It grants nothing.</b> A designation is a label. Who can see and do what comes from the
 * reporting tree ({@link OrgScope}) and the role ladder, neither of which reads this table — which is
 * exactly why it is safe to let a customer edit it. A permission you could award yourself by renaming
 * your own row would not be a permission.
 */
@Entity
@Table(name = "designations")
public class Designation {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    /**
     * Position in the ladder, ascending. Sparse by convention (0, 10, 20) so a rung can be inserted
     * between two others without rewriting every row below it.
     */
    @Column(nullable = false)
    private int level;

    /**
     * Archived rather than deleted. People keep the designation they held, so removing the row would
     * either orphan their history or need a cascade that quietly clears it off their profile; archived
     * hides it from the picker while every existing assignment stays readable.
     */
    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Designation() {
    }

    public Designation(UUID id, UUID companyId, String name, int level) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.level = level;
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
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getCompanyId() { return companyId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public int getLevel() { return level; }

    public void setLevel(int level) { this.level = level; }

    public boolean isArchived() { return archived; }

    public void setArchived(boolean archived) { this.archived = archived; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
