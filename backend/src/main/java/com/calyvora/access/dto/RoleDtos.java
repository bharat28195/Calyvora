package com.calyvora.access.dto;

import com.calyvora.access.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Requests and responses for roles and permissions (PD-54). */
public final class RoleDtos {

    private RoleDtos() {
    }

    /** One permission as the roles screen lists it. */
    public record PermissionInfo(String key, String group, String label, String description, boolean scoped) {
        public static PermissionInfo of(Permission p) {
            return new PermissionInfo(p.name(), p.group(), p.label(), p.description(), p.scoped());
        }
    }

    /**
     * A role, with what it may do. {@code builtin} names which built-in it is (null for a custom role);
     * {@code locked} means its permissions cannot be edited (the Admin role).
     */
    public record RoleResponse(String id, String name, String description, String builtin, boolean locked,
                               Map<String, String> permissions, long memberCount) {
    }

    /** {@code permissions} maps a permission key to COMPANY or TEAM. */
    public record CreateRoleRequest(@NotBlank @Size(max = 60) String name,
                                    @Size(max = 300) String description,
                                    Map<String, String> permissions) {
    }

    /** Partial: a null field is left as it is. */
    public record UpdateRoleRequest(@Size(min = 1, max = 60) String name,
                                    @Size(max = 300) String description,
                                    Map<String, String> permissions) {
    }

    public record AssignRoleRequest(@NotBlank String roleId) {
    }
}
