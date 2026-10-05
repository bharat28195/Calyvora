package com.calyvora.access;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.UUID;

/**
 * One permission held by a role, at a scope. Carries {@code companyId} itself because the grant table
 * is under forced row-level security, and the database checks every inserted row's company.
 */
@Embeddable
public class RoleGrant {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PermissionScope scope;

    protected RoleGrant() {
    }

    public RoleGrant(UUID companyId, Permission permission, PermissionScope scope) {
        this.companyId = companyId;
        this.permission = permission;
        this.scope = scope;
    }

    public Permission getPermission() {
        return permission;
    }

    public PermissionScope getScope() {
        return scope;
    }

    // A role holds a permission once: equality is the permission alone.
    @Override
    public boolean equals(Object o) {
        return o instanceof RoleGrant g && g.permission == permission;
    }

    @Override
    public int hashCode() {
        return Objects.hash(permission);
    }
}
