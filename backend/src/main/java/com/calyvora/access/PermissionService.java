package com.calyvora.access;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * What the caller may do, and for whom (PD-54). The one place a company-level access question is
 * answered; {@link PermissionCheck} exposes it to {@code @PreAuthorize}.
 *
 * <p>Resolved from the database on each request rather than carried in the access token, so an admin
 * who changes a role or takes a permission away is obeyed on the very next request, not when the
 * token expires. Memoised per request, because one request can ask several times.
 *
 * <p>The answer, in order: the platform owner holds everything (the console roles had every
 * {@code hasAnyRole} list); an agency owner holds nothing inside a company; anyone else holds their
 * assigned company role, or — when they have none — the built-in for their {@code users.role}, as
 * the company has edited it, or as it ships.
 */
@Service
public class PermissionService {

    private static final String CACHE = PermissionService.class.getName() + ".grants.";

    private final UserRepository users;
    private final CompanyRoleRepository roles;

    public PermissionService(UserRepository users, CompanyRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    /** Every permission the caller holds, with its scope. Empty for an anonymous or unknown caller. */
    public Map<Permission, PermissionScope> grants(AuthPrincipal principal) {
        if (principal == null) return Map.of();
        if ("OWNER".equals(principal.role())) return BuiltinRole.ADMIN.defaults();
        if ("AGENCY_OWNER".equals(principal.role())) return Map.of();

        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        String key = CACHE + principal.userId();
        if (request != null) {
            @SuppressWarnings("unchecked")
            Map<Permission, PermissionScope> cached =
                    (Map<Permission, PermissionScope>) request.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
            if (cached != null) return cached;
        }
        Map<Permission, PermissionScope> resolved = Collections.unmodifiableMap(resolve(principal.userId()));
        if (request != null) request.setAttribute(key, resolved, RequestAttributes.SCOPE_REQUEST);
        return resolved;
    }

    private Map<Permission, PermissionScope> resolve(UUID userId) {
        User user = users.findById(userId).orElse(null);
        if (user == null) return Map.of();
        if (user.getCompanyRoleId() != null) {
            CompanyRole role = roles.findByIdAndCompanyId(user.getCompanyRoleId(), user.getCompanyId()).orElse(null);
            if (role != null) return role.permissions();
        }
        BuiltinRole builtin = BuiltinRole.forUserRole(user.getRole());
        if (builtin == null) return Map.of();
        return roles.findByCompanyIdAndBuiltin(user.getCompanyId(), builtin)
                .map(CompanyRole::permissions)
                .orElseGet(builtin::defaults);
    }

    public boolean has(AuthPrincipal principal, Permission permission) {
        return grants(principal).containsKey(permission);
    }

    /** Held at company scope: reaches everyone, not only the caller's own reports. */
    public boolean companyWide(AuthPrincipal principal, Permission permission) {
        return grants(principal).get(permission) == PermissionScope.COMPANY;
    }

    /** The caller's scope for a permission, or null when they do not hold it. */
    public PermissionScope scope(AuthPrincipal principal, Permission permission) {
        return grants(principal).get(permission);
    }

    public boolean has(Permission permission) {
        return has(current(), permission);
    }

    public boolean companyWide(Permission permission) {
        return companyWide(current(), permission);
    }

    /** The caller on this thread, as the JWT filter put it there. */
    public static AuthPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }
}
