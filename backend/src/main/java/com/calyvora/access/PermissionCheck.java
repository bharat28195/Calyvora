package com.calyvora.access;

import org.springframework.stereotype.Component;

/**
 * {@link PermissionService} for {@code @PreAuthorize}: {@code @perm.has('PAYROLL_MANAGE')}.
 *
 * <p>Permission names are strings here because SpEL annotations cannot reference an enum constant
 * neatly; an unknown name throws, so a typo fails every test that touches the endpoint rather than
 * quietly denying (or, worse, quietly allowing).
 */
@Component("perm")
public class PermissionCheck {

    private final PermissionService permissions;
    private final com.calyvora.people.OrgScope orgScope;

    public PermissionCheck(PermissionService permissions, com.calyvora.people.OrgScope orgScope) {
        this.permissions = permissions;
        this.orgScope = orgScope;
    }

    /**
     * May see this employee's salary: {@code SALARY_VIEW} company-wide, or at team scope when the
     * employee is somewhere beneath the caller in the reporting tree.
     */
    public boolean seesSalaryOf(java.util.UUID employeeId) {
        com.calyvora.common.security.AuthPrincipal caller = PermissionService.current();
        PermissionScope scope = permissions.scope(caller, Permission.SALARY_VIEW);
        if (scope == PermissionScope.COMPANY) return true;
        return scope == PermissionScope.TEAM && orgScope.downline(caller, false).contains(employeeId);
    }

    /** Holds the permission at any scope. */
    public boolean has(String permission) {
        return permissions.has(Permission.valueOf(permission));
    }

    /** Holds it company-wide. */
    public boolean companyWide(String permission) {
        return permissions.companyWide(Permission.valueOf(permission));
    }
}
