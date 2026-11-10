package com.calyvora.access;

import com.calyvora.identity.Role;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The four roles every company starts with, and what each may do by default.
 *
 * <p>These defaults are today's behaviour, written down: the {@code hasAnyRole} lists and inline
 * role checks they replace were mapped one by one (PD-54). A person with no custom role gets the
 * defaults for their {@code users.role}, so introducing roles changed nobody's access.
 *
 * <p>ADMIN is locked to every permission — a company must never be able to lock itself out of its
 * own settings by editing its admin role. The others can be edited by an admin.
 */
public enum BuiltinRole {

    ADMIN("Admin", "Runs the company account: everything, everywhere."),
    HR("HR", "People operations: people, time, payroll, hiring and documents."),
    MANAGER("Manager", "Leads a team: approves their own people's leave, expenses and attendance."),
    EMPLOYEE("Employee", "Self-service. Anyone with people reporting to them can approve for their team.");

    /** What only the company's administrators do; everything else HR does too. */
    private static final Set<Permission> ADMIN_ONLY = EnumSet.of(
            Permission.MEMBERS_MANAGE, Permission.COMPANY_SETTINGS, Permission.BILLING_MANAGE,
            Permission.FEED_MODERATE, Permission.EXITS_APPROVE);

    /**
     * Approving for one's own reports. Granted to MANAGER and EMPLOYEE alike because the server never
     * distinguished them: reach comes from having reports, not from the title (PD-32, PD-51).
     */
    private static final Set<Permission> TEAM_APPROVALS = EnumSet.of(
            Permission.LEAVE_APPROVE, Permission.EXPENSES_APPROVE, Permission.ATTENDANCE_MANAGE);

    private final String label;
    private final String description;

    BuiltinRole(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean locked() {
        return this == ADMIN;
    }

    /** The default grants for this built-in. */
    public Map<Permission, PermissionScope> defaults() {
        Map<Permission, PermissionScope> grants = new EnumMap<>(Permission.class);
        switch (this) {
            case ADMIN -> {
                for (Permission p : Permission.values()) grants.put(p, PermissionScope.COMPANY);
            }
            case HR -> {
                for (Permission p : Permission.values()) {
                    if (!ADMIN_ONLY.contains(p)) grants.put(p, PermissionScope.COMPANY);
                }
            }
            case MANAGER, EMPLOYEE -> {
                for (Permission p : TEAM_APPROVALS) grants.put(p, PermissionScope.TEAM);
            }
        }
        return Collections.unmodifiableMap(grants);
    }

    /** The built-in a legacy {@code users.role} corresponds to; null for the two console roles. */
    public static BuiltinRole forUserRole(Role role) {
        return switch (role) {
            case ADMIN -> ADMIN;
            case HR -> HR;
            case MANAGER -> MANAGER;
            case MEMBER -> EMPLOYEE;
            case OWNER, AGENCY_OWNER -> null;
        };
    }

    /** The {@code users.role} kept in step when someone is given this built-in. */
    public Role userRole() {
        return switch (this) {
            case ADMIN -> Role.ADMIN;
            case HR -> Role.HR;
            case MANAGER -> Role.MANAGER;
            case EMPLOYEE -> Role.MEMBER;
        };
    }
}
