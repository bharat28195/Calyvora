package com.calyvora.access;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A role in one company: a name and the permissions it carries (PD-54). */
@Entity
@Table(name = "company_roles")
public class CompanyRole {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    /** Which built-in this is, or null for a role the company made. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BuiltinRole builtin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleGrant> grants = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyRole() {
    }

    public CompanyRole(UUID id, UUID companyId, String name, String description, BuiltinRole builtin) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.builtin = builtin;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BuiltinRole getBuiltin() { return builtin; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }

    /** The grants as a map. An ADMIN built-in always answers every permission, whatever is stored. */
    public Map<Permission, PermissionScope> permissions() {
        if (builtin == BuiltinRole.ADMIN) {
            return BuiltinRole.ADMIN.defaults();
        }
        Map<Permission, PermissionScope> map = new EnumMap<>(Permission.class);
        for (RoleGrant g : grants) map.put(g.getPermission(), g.getScope());
        return map;
    }

    /** Replace every grant. A scope given for an unscoped permission is stored as COMPANY. */
    public void setPermissions(Map<Permission, PermissionScope> next) {
        grants.clear();
        next.forEach((p, s) -> grants.add(new RoleGrant(companyId, p,
                p.scoped() && s == PermissionScope.TEAM ? PermissionScope.TEAM : PermissionScope.COMPANY)));
    }
}
