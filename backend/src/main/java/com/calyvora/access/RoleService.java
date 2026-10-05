package com.calyvora.access;

import com.calyvora.access.dto.RoleDtos.AssignRoleRequest;
import com.calyvora.access.dto.RoleDtos.CreateRoleRequest;
import com.calyvora.access.dto.RoleDtos.PermissionInfo;
import com.calyvora.access.dto.RoleDtos.RoleResponse;
import com.calyvora.access.dto.RoleDtos.UpdateRoleRequest;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A company's roles: the four built-ins plus any it makes, and who holds which (PD-54).
 *
 * <p>The guard rails, each of which exists because the alternative locks a company out of itself or
 * leaves access in a state nobody chose: the Admin role cannot be edited; nobody changes their own
 * role; the last administrator cannot be moved off Admin; a role somebody holds cannot be deleted.
 */
@Service
public class RoleService {

    private final CompanyRoleRepository roles;
    private final UserRepository users;

    public RoleService(CompanyRoleRepository roles, UserRepository users) {
        this.roles = roles;
        this.users = users;
    }

    public List<PermissionInfo> catalogue() {
        return Arrays.stream(Permission.values()).map(PermissionInfo::of).toList();
    }

    @Transactional
    public List<RoleResponse> list() {
        UUID companyId = TenantContext.getCompanyId();
        ensureBuiltins(companyId);
        List<User> people = users.findByCompanyIdOrderByCreatedAtAsc(companyId);
        return roles.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .sorted((a, b) -> Integer.compare(order(a), order(b)))
                .map(r -> response(r, people))
                .toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest req) {
        UUID companyId = TenantContext.getCompanyId();
        ensureBuiltins(companyId);
        String name = req.name().trim();
        if (roles.existsByCompanyIdAndNameIgnoreCase(companyId, name)) {
            throw new ApiException(ErrorCode.CONFLICT, "There is already a role called " + name);
        }
        CompanyRole role = new CompanyRole(UUID.randomUUID(), companyId, name, blankToNull(req.description()), null);
        role.setPermissions(parse(req.permissions()));
        roles.save(role);
        return response(role, users.findByCompanyIdOrderByCreatedAtAsc(companyId));
    }

    @Transactional
    public RoleResponse update(UUID roleId, UpdateRoleRequest req) {
        UUID companyId = TenantContext.getCompanyId();
        CompanyRole role = require(companyId, roleId);
        if (role.getBuiltin() != null && req.name() != null && !req.name().trim().equals(role.getName())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Built-in roles keep their names. Make a new role instead.");
        }
        if (role.getBuiltin() == null && req.name() != null) {
            String name = req.name().trim();
            if (!name.equalsIgnoreCase(role.getName()) && roles.existsByCompanyIdAndNameIgnoreCase(companyId, name)) {
                throw new ApiException(ErrorCode.CONFLICT, "There is already a role called " + name);
            }
            role.setName(name);
        }
        if (req.description() != null) {
            role.setDescription(blankToNull(req.description()));
        }
        if (req.permissions() != null) {
            if (role.getBuiltin() != null && role.getBuiltin().locked()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "The Admin role always has every permission, so a company can never lock itself out.");
            }
            role.setPermissions(parse(req.permissions()));
        }
        return response(roles.save(role), users.findByCompanyIdOrderByCreatedAtAsc(companyId));
    }

    @Transactional
    public void delete(UUID roleId) {
        UUID companyId = TenantContext.getCompanyId();
        CompanyRole role = require(companyId, roleId);
        if (role.getBuiltin() != null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Built-in roles can't be deleted.");
        }
        long holders = users.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .filter(u -> roleId.equals(u.getCompanyRoleId())).count();
        if (holders > 0) {
            throw new ApiException(ErrorCode.CONFLICT,
                    holders + " " + (holders == 1 ? "person has" : "people have") + " this role. Move them to another role first.");
        }
        roles.delete(role);
    }

    /** Give somebody a role. Built-ins keep users.role in step; a custom role leaves them a MEMBER underneath. */
    @Transactional
    public void assign(UUID userId, AssignRoleRequest req, AuthPrincipal caller) {
        UUID companyId = TenantContext.getCompanyId();
        if (userId.equals(caller.userId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "You can't change your own role. Ask another admin.");
        }
        User user = users.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new NotFoundException("No such member"));
        if (user.getRole() == Role.OWNER || user.getRole() == Role.AGENCY_OWNER) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "That account's role is managed by the platform.");
        }
        CompanyRole role = require(companyId, parseId(req.roleId()));

        boolean wasAdmin = holdsAdmin(user);
        boolean staysAdmin = role.getBuiltin() == BuiltinRole.ADMIN;
        if (wasAdmin && !staysAdmin && activeAdmins(companyId) <= 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This is the company's only admin. Make someone else an admin first.");
        }

        if (role.getBuiltin() != null) {
            user.setRole(role.getBuiltin().userRole());
            user.setCompanyRoleId(null);   // "the built-in for my role" — the same thing, without a pointer to keep valid
        } else {
            user.setRole(Role.MEMBER);
            user.setCompanyRoleId(role.getId());
        }
        users.save(user);
    }

    // ---- helpers ----

    /** The four built-ins exist as rows from the first time anyone looks at or edits roles. */
    private void ensureBuiltins(UUID companyId) {
        for (BuiltinRole b : BuiltinRole.values()) {
            if (roles.findByCompanyIdAndBuiltin(companyId, b).isEmpty()) {
                CompanyRole row = new CompanyRole(UUID.randomUUID(), companyId, b.label(), b.description(), b);
                row.setPermissions(b.defaults());
                roles.save(row);
            }
        }
        roles.flush();
    }

    private boolean holdsAdmin(User u) {
        return u.getRole() == Role.ADMIN && u.getCompanyRoleId() == null;
    }

    private long activeAdmins(UUID companyId) {
        return users.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && holdsAdmin(u))
                .count();
    }

    private RoleResponse response(CompanyRole r, List<User> people) {
        long count = people.stream().filter(u -> holds(u, r)).count();
        Map<String, String> perms = new LinkedHashMap<>();
        r.permissions().forEach((p, s) -> perms.put(p.name(), s.name()));
        return new RoleResponse(r.getId().toString(), r.getName(), r.getDescription(),
                r.getBuiltin() == null ? null : r.getBuiltin().name(),
                r.getBuiltin() != null && r.getBuiltin().locked(), perms, count);
    }

    private static boolean holds(User u, CompanyRole r) {
        if (u.getCompanyRoleId() != null) return u.getCompanyRoleId().equals(r.getId());
        return r.getBuiltin() != null && r.getBuiltin() == BuiltinRole.forUserRole(u.getRole());
    }

    private static int order(CompanyRole r) {
        return r.getBuiltin() == null ? 100 : r.getBuiltin().ordinal();
    }

    private CompanyRole require(UUID companyId, UUID roleId) {
        return roles.findByIdAndCompanyId(roleId, companyId)
                .orElseThrow(() -> new NotFoundException("No such role"));
    }

    private static Map<Permission, PermissionScope> parse(Map<String, String> raw) {
        Map<Permission, PermissionScope> out = new EnumMap<>(Permission.class);
        if (raw == null) return out;
        raw.forEach((k, v) -> {
            Permission p;
            PermissionScope s;
            try {
                p = Permission.valueOf(k);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "No such permission: " + k);
            }
            try {
                s = v == null ? PermissionScope.COMPANY : PermissionScope.valueOf(v);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "A scope is COMPANY or TEAM, not " + v);
            }
            out.put(p, s);
        });
        return out;
    }

    private static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid role id");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
