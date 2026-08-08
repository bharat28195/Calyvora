package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.people.dto.EmployeeResponse;

import java.util.List;
import java.util.Set;

/**
 * Who may see whose performance rating.
 *
 * <p>The directory is readable by every member of a company — that's deliberate, it's how people
 * find each other. The rating rode along in the same payload, which meant any employee could read
 * every colleague's score. Salary was already protected; this closes the same gap for ratings.
 *
 * <p>Visible to HR and company leadership, to the person themselves, and to that person's manager.
 */
final class RatingVisibility {

    private static final Set<String> PRIVILEGED_ROLES = Set.of("OWNER", "ADMIN", "HR");

    private RatingVisibility() {}

    static List<EmployeeResponse> filter(List<EmployeeResponse> employees, AuthPrincipal viewer) {
        if (isPrivileged(viewer)) {
            return employees;
        }
        String viewerEmployeeId = employees.stream()
                .filter(e -> e.userId().equals(viewer.userId().toString()))
                .map(EmployeeResponse::id)
                .findFirst()
                .orElse(null);
        return employees.stream().map(e -> visible(e, viewer, viewerEmployeeId) ? e : e.withoutRating()).toList();
    }

    static EmployeeResponse filter(EmployeeResponse employee, AuthPrincipal viewer, String viewerEmployeeId) {
        return isPrivileged(viewer) || visible(employee, viewer, viewerEmployeeId)
                ? employee
                : employee.withoutRating();
    }

    private static boolean visible(EmployeeResponse e, AuthPrincipal viewer, String viewerEmployeeId) {
        boolean isSelf = e.userId().equals(viewer.userId().toString());
        boolean isMyReport = viewerEmployeeId != null && viewerEmployeeId.equals(e.managerId());
        return isSelf || isMyReport;
    }

    private static boolean isPrivileged(AuthPrincipal viewer) {
        return viewer != null && PRIVILEGED_ROLES.contains(viewer.role());
    }
}
